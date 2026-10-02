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

    fun isPhone(recipient: String) = PHONE.matches(recipient.trim())

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
