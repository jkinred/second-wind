package io.github.jkinred.secondwind.data

/** Pure inbound bookkeeping: reassembly by device thread id. */
object Inbox {
    fun receive(
        messages: List<Message>,
        ybId: Int,
        totalParts: Int,
        partNo: Int,
        threadId: Int,
        sender: String,
        text: String,
        credit: Int?,
        now: Long,
    ): List<Message> {
        if (totalParts <= 1) {
            return messages + Message(
                id = "in-$ybId-$now", direction = Direction.IN, at = now,
                from = sender, inParts = mapOf(1 to text), totalParts = 1, unread = true, creditAfter = credit,
            )
        }
        val open = messages.indexOfFirst { it.direction == Direction.IN && it.threadId == threadId && !it.complete }
        if (open < 0) {
            return messages + Message(
                id = "in-t$threadId-$now", direction = Direction.IN, at = now,
                from = sender, threadId = threadId, inParts = mapOf(partNo to text), totalParts = totalParts, unread = true, creditAfter = credit,
            )
        }
        val m = messages[open]
        val merged = m.copy(
            from = m.from.ifEmpty { sender },
            creditAfter = credit ?: m.creditAfter,
            inParts = m.inParts + (partNo to text),
            totalParts = maxOf(m.totalParts, totalParts),
        )
        return messages.toMutableList().also { it[open] = merged }
    }
}
