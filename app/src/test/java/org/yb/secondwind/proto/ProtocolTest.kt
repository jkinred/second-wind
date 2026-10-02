package org.yb.secondwind.proto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Vectors from docs/PROTOCOL.md §6. Keyword `abcd`, password `wxyz`. */
class ProtocolTest {
    private val kw = Keyword.of("abcd")
    private val creds = Credentials.of("abcd", "wxyz")

    @Test fun statusRequest() = assertEquals("01000961626364000006019904", Outbound.statusRequest(kw).toHex())
    @Test fun request() = assertEquals("01000961626364000002019504", Outbound.request(kw).toHex())
    @Test fun ack() = assertEquals("01000b61626364000004123401df04", Outbound.ack(kw, 0x1234).toHex())
    @Test fun mailboxCheck() = assertEquals("01000961626364000005019804", Outbound.mailboxCheck(kw).toHex())

    @Test fun textOutSinglePart() = assertEquals(
        "01001e6162636400000112340101017778797a616263646140622e633a686907fd04",
        Outbound.textOut(creds, TextOutPart(0x1234, "a@b.c:hi")).toHex(),
    )

    @Test fun modeCommand() = assertEquals(
        "0100176162636400000100010001007778797a6162636453056304",
        Outbound.setMode(creds, Mode.STAND_ALONE, 0x0001).toHex(),
    )

    @Test fun multiPartCarriesPartAndThread() {
        val f = Outbound.textOut(creds, TextOutPart(0x0102, "x", totalParts = 3, partNo = 2, threadId = 7, includePosition = false))
        // body starts at offset 10: ybId(2) pos now total part thread
        assertArrayEquals(byteArrayOf(1, 2, 0, 1, 3, 2, 7), f.copyOfRange(10, 17))
    }

    @Test fun decodesCapturedStatus() {
        val frames = FrameDecoder().feed("01000f0000000000008302070200050000a204".hexToBytes())
        val f = frames.single()
        assertTrue(f.checksumOk)
        val s = (Inbound.parse(f) as Inbound.Status).status
        assertEquals("02.07.02", s.firmware)
        assertEquals(false, s.storageNearlyFull)
        assertEquals("30 min", s.trackingInterval)
        assertEquals("5 min", s.inboxCheckInterval)
    }

    @Test fun shortStatusLeavesFieldsUnknown() {
        val s = DeviceStatus.parse(byteArrayOf(2, 7, 2))
        assertEquals("02.07.02", s.firmware)
        assertNull(s.storageNearlyFull); assertNull(s.trackingInterval); assertNull(s.inboxCheckInterval)
    }

    @Test fun decodesNoTextAcrossSplitReads() {
        val d = FrameDecoder()
        val raw = "01000900000000000081008a04".hexToBytes()
        assertEquals(0, d.feed(raw.copyOfRange(0, 5)).size)
        val f = d.feed(raw.copyOfRange(5, raw.size)).single()
        assertTrue(f.checksumOk)
        assertTrue(Inbound.parse(f) is Inbound.NoText)
    }

    @Test fun decoderSkipsGarbageAndFlagsBadChecksum() {
        val good = "01000900000000000081008a04".hexToBytes()
        val bad = good.copyOf().also { it[it.size - 2] = 0x00 }
        val frames = FrameDecoder().feed(byteArrayOf(0x55, 0x66) + bad + good)
        assertEquals(2, frames.size)
        assertFalse(frames[0].checksumOk)
        assertTrue(frames[1].checksumOk)
    }

    @Test fun parsesSingleAndMultiPartTextIn() {
        val single = Inbound.parse(Frame(Type.TEXT_IN, "0a0b0005".hexToBytes() + byteArrayOf(1) + "bob@x.com:hello".toByteArray(), true)) as Inbound.Text
        assertEquals(0x0a0b, single.ybId); assertEquals(5, single.credit)
        assertEquals("bob@x.com", single.sender); assertEquals("hello", single.text)
        assertEquals(1, single.totalParts); assertEquals(1, single.partNo)

        val p1 = Inbound.parse(Frame(Type.TEXT_IN, "0a0cffff".hexToBytes() + byteArrayOf(2, 1, 9) + "bob@x.com:first".toByteArray(), true)) as Inbound.Text
        assertNull(p1.credit); assertEquals(2, p1.totalParts); assertEquals(1, p1.partNo); assertEquals(9, p1.threadId)
        assertEquals("bob@x.com", p1.sender); assertEquals("first", p1.text)

        val p2 = Inbound.parse(Frame(Type.TEXT_IN, "0a0d0005".hexToBytes() + byteArrayOf(2, 2, 9) + "second:with:colons".toByteArray(), true)) as Inbound.Text
        assertEquals(2, p2.partNo); assertEquals("", p2.sender); assertEquals("second:with:colons", p2.text)
    }

    @Test fun shortTextInMeansInvalidCredentials() {
        val r = Inbound.parse(Frame(Type.TEXT_IN, "00010002".hexToBytes(), true))
        assertTrue(r is Inbound.InvalidCredentials)
    }

    @Test fun confirmationCarriesYbId() =
        assertEquals(0x1234, (Inbound.parse(Frame(Type.CONFIRMATION, "1234".hexToBytes(), true)) as Inbound.Confirmation).ybId)
}
