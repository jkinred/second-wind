package io.github.jkinred.secondwind.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadTest {
    @Test fun buildsHeaderWithNormalisedRecipientsAndGroup() {
        assertEquals("a@b.c,+447700900123,g:hi", Payload.build(listOf(" A@B.c ", "+447700900123"), "hi", group = true))
        assertEquals("x-y-z:t", Payload.build(listOf("x:y,z"), "t", group = false))
        assertEquals("g:t", Payload.build(emptyList(), "t", group = true))
    }

    @Test fun splitBoundaries() {
        assertEquals(listOf(307), Payload.split("x".repeat(307)).map { it.length })
        assertEquals(listOf(305, 3), Payload.split("x".repeat(308)).map { it.length })
        assertEquals(listOf(305, 321, 10), Payload.split("x".repeat(305 + 321 + 10)).map { it.length })
    }

    @Test fun phoneDetection() {
        assertTrue(Payload.isPhone("+44 7700 900123"))
        assertTrue(Payload.isPhone("07700900123"))
        assertFalse(Payload.isPhone("a@b.c"))
        assertFalse(Payload.isPhone("g"))
    }

    @Test fun creditEstimateIncludesAddressesAndSmsSurcharge() {
        // "a@b.c:" is 6 chars + 44 text = 50 → 1 credit; no SMS
        val e1 = Payload.estimate(listOf("a@b.c"), "x".repeat(44), group = false)
        assertEquals(50, e1.chars); assertEquals(1, e1.credits); assertEquals(0, e1.smsRecipients)
        // one more char → 2 credits
        assertEquals(2, Payload.estimate(listOf("a@b.c"), "x".repeat(45), group = false).credits)
        // phone recipient: "+447700900123:" = 14 chars + 10 text = 24 → 1 + 1 SMS = 2
        val e2 = Payload.estimate(listOf("+447700900123"), "x".repeat(10), group = false)
        assertEquals(2, e2.credits); assertEquals(1, e2.smsRecipients)
    }
}
