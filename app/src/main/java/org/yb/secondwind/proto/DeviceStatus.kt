package org.yb.secondwind.proto

/** STATUS body. docs/PROTOCOL.md §4.8. Unknown fields are null. */
class DeviceStatus(
    val firmware: String,
    /** True when the device holds ≥ 490 of 500 shared message records. */
    val storageNearlyFull: Boolean?,
    /** Configured tracking interval; enabled state is not reported. */
    val trackingInterval: String?,
    /** Configured satellite inbox-check interval; enabled state is not reported. */
    val inboxCheckInterval: String?,
) {
    companion object {
        private val TRACKING = listOf(
            "continuous", "5 min", "10 min", "15 min", "20 min", "30 min", "1 h", "90 min",
            "2 h", "3 h", "4 h", "6 h", "8 h", "12 h", "burst",
        )
        private val INBOX = listOf(
            "5 min", "10 min", "15 min", "20 min", "30 min", "1 h", "90 min",
            "2 h", "3 h", "4 h", "6 h", "8 h", "12 h",
        )

        fun parse(b: ByteArray): DeviceStatus {
            val fw = if (b.size >= 3) "%02d.%02d.%02d".format(b[0], b[1], b[2]) else b.toHex()
            return DeviceStatus(
                firmware = fw,
                storageNearlyFull = b.getOrNull(3)?.let { it.toInt() != 0 },
                trackingInterval = b.getOrNull(4)?.let { TRACKING.getOrNull(it.toInt() and 0xFF) },
                inboxCheckInterval = b.getOrNull(5)?.let { INBOX.getOrNull(it.toInt() and 0xFF) },
            )
        }
    }
}
