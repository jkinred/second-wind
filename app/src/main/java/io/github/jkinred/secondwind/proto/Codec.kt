package io.github.jkinred.secondwind.proto

/**
 * Frame codec for the Yellowbrick v3 Bluetooth interface. docs/PROTOCOL.md §2.
 *
 * 01 | len u16 | header(6) | type | body | sum u16 | 04
 */
object Codec {
    const val SOF: Byte = 0x01
    const val EOT: Byte = 0x04
    private const val MIN_LEN = 9 // len u16 + header(6) + type

    fun encode(keyword: Keyword, type: Int, body: ByteArray = EMPTY): ByteArray {
        val len = MIN_LEN + body.size
        val frame = ByteArray(1 + len + 3)
        frame[0] = SOF
        frame[1] = (len ushr 8).toByte()
        frame[2] = len.toByte()
        keyword.bytes.copyInto(frame, 3)
        // frame[7], frame[8] stay 0x00
        frame[9] = type.toByte()
        body.copyInto(frame, 10)
        val sum = sum16(frame, 1, 1 + len)
        frame[1 + len] = (sum ushr 8).toByte()
        frame[2 + len] = sum.toByte()
        frame[3 + len] = EOT
        return frame
    }

    internal fun sum16(b: ByteArray, from: Int, until: Int): Int {
        var s = 0
        for (i in from until until) s += b[i].toInt() and 0xFF
        return s and 0xFFFF
    }

    internal val EMPTY = ByteArray(0)
}

class Frame(val type: Int, val body: ByteArray, val checksumOk: Boolean)

/** Incremental decoder: feed stream chunks, collect complete frames. */
class FrameDecoder {
    private var buf = ByteArray(0)

    fun feed(data: ByteArray, len: Int = data.size): List<Frame> {
        buf = if (buf.isEmpty()) data.copyOf(len) else buf + data.copyOf(len)
        val out = ArrayList<Frame>(1)
        while (true) {
            val start = buf.indexOf(Codec.SOF)
            if (start < 0) { buf = Codec.EMPTY; return out }
            if (start > 0) buf = buf.copyOfRange(start, buf.size)
            if (buf.size < 3) return out
            val length = ((buf[1].toInt() and 0xFF) shl 8) or (buf[2].toInt() and 0xFF)
            if (length < 9) { buf = buf.copyOfRange(1, buf.size); continue }
            val total = 1 + length + 3
            if (buf.size < total) return out
            val declared = ((buf[1 + length].toInt() and 0xFF) shl 8) or (buf[2 + length].toInt() and 0xFF)
            val ok = Codec.sum16(buf, 1, 1 + length) == declared
            out += Frame(type = buf[9].toInt() and 0xFF, body = buf.copyOfRange(10, 1 + length), checksumOk = ok)
            buf = buf.copyOfRange(total, buf.size)
        }
    }
}

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
fun String.hexToBytes(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()
