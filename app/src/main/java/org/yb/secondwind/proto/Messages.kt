package org.yb.secondwind.proto

/** Message types. docs/PROTOCOL.md §4. */
object Type {
    const val TEXT_OUT = 0x01
    const val REQUEST = 0x02
    const val ACK = 0x04
    const val MAILBOX_CHECK = 0x05
    const val STATUS_REQUEST = 0x06

    const val CONFIRMATION = 0x80
    const val NO_TEXT = 0x81
    const val TEXT_IN = 0x82
    const val STATUS = 0x83

    fun name(t: Int): String = when (t) {
        TEXT_OUT -> "TEXT_OUT"
        REQUEST -> "REQUEST"
        ACK -> "ACK"
        MAILBOX_CHECK -> "MAILBOX_CHECK"
        STATUS_REQUEST -> "STATUS_REQUEST"
        CONFIRMATION -> "CONFIRMATION"
        NO_TEXT -> "NO_TEXT"
        TEXT_IN -> "TEXT_IN"
        STATUS -> "STATUS"
        else -> "0x%02x".format(t)
    }
}

enum class Mode(val code: String) { STAND_ALONE("S"), HOTSPOT("H") }

/** One TEXT_OUT part. docs/PROTOCOL.md §4.1. */
class TextOutPart(
    val ybId: Int,
    val payload: String,
    val totalParts: Int = 1,
    val partNo: Int = 1,
    val threadId: Int = 0,
    val includePosition: Boolean = true,
    val sendNow: Boolean = true,
)

/** Outbound frame builders. */
object Outbound {
    fun request(k: Keyword) = Codec.encode(k, Type.REQUEST)
    fun statusRequest(k: Keyword) = Codec.encode(k, Type.STATUS_REQUEST)
    fun mailboxCheck(k: Keyword) = Codec.encode(k, Type.MAILBOX_CHECK)
    fun ack(k: Keyword, ybId: Int) = Codec.encode(k, Type.ACK, u16(ybId))

    fun textOut(c: Credentials, p: TextOutPart): ByteArray {
        val text = p.payload.toByteArray(Charsets.ISO_8859_1)
        val multi = p.totalParts > 1
        val body = ByteArray(5 + (if (multi) 2 else 0) + 8 + text.size)
        var i = 0
        body[i++] = (p.ybId ushr 8).toByte()
        body[i++] = p.ybId.toByte()
        body[i++] = if (p.includePosition) 1 else 0
        body[i++] = if (p.sendNow) 1 else 0
        body[i++] = p.totalParts.toByte()
        if (multi) {
            body[i++] = p.partNo.toByte()
            body[i++] = p.threadId.toByte()
        }
        c.password.bytes.copyInto(body, i); i += 4
        c.keyword.bytes.copyInto(body, i); i += 4
        text.copyInto(body, i)
        return Codec.encode(c.keyword, Type.TEXT_OUT, body)
    }

    /** Mode command: TEXT_OUT with totalParts = 0 and payload S/H. §4.2. */
    fun setMode(c: Credentials, mode: Mode, ybId: Int) =
        textOut(c, TextOutPart(ybId, mode.code, totalParts = 0, partNo = 0, includePosition = false))

    private fun u16(v: Int) = byteArrayOf((v ushr 8).toByte(), v.toByte())
}

/** Decoded inbound frames. docs/PROTOCOL.md §4.5–4.8. */
sealed interface Inbound {
    class Confirmation(val ybId: Int) : Inbound
    data object NoText : Inbound
    class InvalidCredentials(val ybId: Int, val credit: Int) : Inbound
    class Text(
        val ybId: Int,
        /** Remaining balance, or null when the device reports 0xFFFF. */
        val credit: Int?,
        val totalParts: Int,
        val partNo: Int,
        val threadId: Int,
        /** Empty on continuation parts. */
        val sender: String,
        val text: String,
    ) : Inbound
    class Status(val status: DeviceStatus) : Inbound
    class Unknown(val type: Int, val body: ByteArray) : Inbound

    companion object {
        fun parse(f: Frame): Inbound = when (f.type) {
            Type.CONFIRMATION -> Confirmation(u16(f.body, 0))
            Type.NO_TEXT -> NoText
            Type.TEXT_IN -> parseText(f.body)
            Type.STATUS -> Status(DeviceStatus.parse(f.body))
            else -> Unknown(f.type, f.body)
        }

        private fun parseText(b: ByteArray): Inbound {
            val ybId = u16(b, 0)
            val creditRaw = u16(b, 2)
            if (b.size <= 4) return InvalidCredentials(ybId, creditRaw)
            val credit = creditRaw.takeIf { it != 0xFFFF }
            val total = b[4].toInt() and 0xFF
            val multi = total > 1
            val partNo = if (multi) b[5].toInt() and 0xFF else 1
            val threadId = if (multi) b[6].toInt() and 0xFF else 0
            val textStart = if (multi) 7 else 5
            val s = if (b.size > textStart) String(b, textStart, b.size - textStart, Charsets.ISO_8859_1) else ""
            return if (partNo == 1) {
                val colon = s.indexOf(':')
                if (colon < 0) Text(ybId, credit, total, partNo, threadId, "", s)
                else Text(ybId, credit, total, partNo, threadId, s.substring(0, colon), s.substring(colon + 1))
            } else {
                Text(ybId, credit, total, partNo, threadId, "", s)
            }
        }

        private fun u16(b: ByteArray, off: Int) =
            if (b.size >= off + 2) ((b[off].toInt() and 0xFF) shl 8) or (b[off + 1].toInt() and 0xFF) else 0
    }
}
