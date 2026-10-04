package io.github.jkinred.secondwind.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PayloadTest {
    @Test fun buildsHeaderWithNormalisedRecipientsAndGroup() {
        assertEquals("a@b.c,+447700900123,g:hi", Payload.build(listOf(" A@B.c ", "+447700900123"), "hi", group = true))
        assertEquals("x-y-z:t", Payload.build(listOf("x:y,z"), "t", group = false))
        assertEquals("g:t", Payload.build(emptyList(), "t", group = true))
    }

    @Test fun preparesPhoneWithoutChangingEmailPlusTags() {
        val phone = Payload.prepareRecipient(" +44\t7700-900123\n")!!
        val email = Payload.prepareRecipient(" Alice+tag@Example.com ")!!
        assertEquals("447700900123", phone)
        assertEquals("alice+tag@example.com", email)
        assertEquals("447700900123,alice+tag@example.com:hi", Payload.build(listOf(phone, email), "hi", false))
        assertEquals(phone, Payload.prepareRecipient(phone))
    }

    @Test fun invalidPhoneRequiresCorrectionInsteadOfGuessing() {
        for (input in listOf("", " + - ", "07700900123", "00447700900123", "+44 (0)7700 900123", "44.7700.900123", "44/7700900123", "44:7700900123", "not-a-number", "４４７７００９００１２３")) {
            assertNull(input, Payload.prepareRecipient(input))
        }
    }

    @Test fun validatesInternationalPrefixAndSuffixBoundaries() {
        for (prefix in listOf("1", "7", "44", "61", "210", "269", "280", "299", "999")) {
            assertEquals(prefix, Payload.prepareRecipient(prefix))
            assertEquals(prefix + "0".repeat(14), Payload.prepareRecipient(prefix + "0".repeat(14)))
            assertNull(prefix, Payload.prepareRecipient(prefix + "0".repeat(15)))
            assertTrue(Payload.isPhone(prefix))
        }
        for (prefix in listOf("0", "2", "21", "28", "29", "35", "37", "38", "42", "59", "67", "80", "83", "85", "87", "89", "96", "97", "99")) {
            assertNull(prefix, Payload.prepareRecipient(prefix))
        }
    }

    @Test fun preparedSmsEstimateUsesActualWireLength() {
        val recipients = listOf(Payload.prepareRecipient("+44 7700-900123")!!)
        val estimate = Payload.estimate(recipients, "x".repeat(37), false)
        assertEquals(50, estimate.chars)
        assertEquals(2, estimate.credits)
        assertEquals(1, estimate.smsRecipients)
        assertEquals(3, Payload.estimate(recipients, "x".repeat(38), false).credits)
    }

    @Test fun matchingKeyDoesNotStripEmailTagsOrGuessInvalidNumbers() {
        assertEquals(Payload.recipientKey("447700900123"), Payload.recipientKey("+44 7700-900123"))
        assertEquals("alice+tag@example.com", Payload.recipientKey("Alice+tag@Example.com"))
        assertEquals("07700900123", Payload.recipientKey("07700900123"))
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
