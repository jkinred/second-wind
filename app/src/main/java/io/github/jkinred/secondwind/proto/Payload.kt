package io.github.jkinred.secondwind.proto

/** Payload rules. docs/PROTOCOL.md §4.3–4.4. */
object Payload {
    const val SINGLE_PART_MAX = 307
    const val FIRST_PART_LEN = 305
    const val NEXT_PART_LEN = 321
    const val GROUP = "g"

    private val PHONE = Regex("""^\+?[0-9][0-9 ()\-]{4,}$""")

    fun normaliseRecipient(r: String): String =
        r.trim().replace(':', '-').replace(',', '-').lowercase()

    fun isPhone(recipient: String) = PHONE.matches(recipient.trim()) || preparePhone(recipient) != null

    /** New selections only. Received reply addresses and queued payloads bypass this step. */
    fun prepareRecipient(address: String): String? =
        if ('@' in address) normaliseRecipient(address) else preparePhone(address)

    /** Matching key only; never substitute this for a persisted recipient or reply address. */
    fun recipientKey(address: String): String =
        if ('@' in address) normaliseRecipient(address) else preparePhone(address) ?: normaliseRecipient(address)

    private fun phoneFormatting(c: Char) = c == '+' || c == '-' || c.isWhitespace()

    private fun preparePhone(address: String): String? {
        val digits = if (address.any(::phoneFormatting)) address.filterNot(::phoneFormatting) else address
        if (digits.isEmpty() || digits.length > 17 || digits.any { it !in '0'..'9' }) return null
        var prefix = 0
        for (i in 0 until minOf(3, digits.length)) {
            prefix = prefix * 10 + (digits[i] - '0')
            if (digits.length - i - 1 <= 14 && phonePrefix(prefix, i + 1)) return digits
        }
        return null
    }

    /** International prefix acceptance, docs/PROTOCOL.md §4.3.1. Not national-number validation. */
    private fun phonePrefix(prefix: Int, length: Int): Boolean = when (length) {
        1 -> prefix == 1 || prefix == 7
        2 -> when (prefix) {
            20, 27, in 30..34, 36, in 39..41, in 43..49, in 51..58, in 60..66,
            81, 82, 84, 86, in 90..95, 98 -> true
            else -> false
        }
        3 -> when (prefix) {
            in 210..269, in 280..299, in 350..359, in 370..389, in 420..429,
            in 500..509, in 590..599, in 670..699, in 800..809, in 830..839,
            in 850..859, in 870..899, in 960..979, in 990..999 -> true
            else -> false
        }
        else -> false
    }

    /** `rcpt[,rcpt][,g]:text` */
    fun build(recipients: List<String>, text: String, group: Boolean): String {
        val heads = recipients.map(::normaliseRecipient).toMutableList()
        if (group) heads += GROUP
        return heads.joinToString(",") + ":" + text
    }

    fun split(payload: String): List<String> {
        if (payload.length <= SINGLE_PART_MAX) return listOf(payload)
        val out = ArrayList<String>(2 + (payload.length - FIRST_PART_LEN) / NEXT_PART_LEN)
        out += payload.substring(0, FIRST_PART_LEN)
        out += payload.substring(FIRST_PART_LEN).chunked(NEXT_PART_LEN)
        return out
    }

    class Estimate(val chars: Int, val parts: Int, val credits: Int, val smsRecipients: Int, val group: Boolean)

    /** Vendor-published billing rule: 1 credit per 50 payload chars + 1 per SMS recipient. Group cost unknown. */
    fun estimate(recipients: List<String>, text: String, group: Boolean): Estimate {
        val payload = build(recipients, text, group)
        val sms = recipients.count(::isPhone)
        return Estimate(
            chars = payload.length,
            parts = split(payload).size,
            credits = (payload.length + 49) / 50 + sms,
            smsRecipients = sms,
            group = group,
        )
    }
}
