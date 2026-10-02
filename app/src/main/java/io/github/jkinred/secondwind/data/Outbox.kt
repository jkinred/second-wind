package io.github.jkinred.secondwind.data

import io.github.jkinred.secondwind.proto.Payload

/** Pure outbound bookkeeping; the session applies these to [AppData.messages]. */
object Outbox {
    class Pending(val message: Message, val part: OutPart, val payload: String)

    /** Parts still owed a CONFIRMATION, oldest message first, part order within. Payload slices rebuilt deterministically. */
    fun pending(messages: List<Message>): List<Pending> =
        messages.asSequence()
            .filter { it.direction == Direction.OUT && it.outState != OutState.ACCEPTED }
            .sortedBy { it.at }
            .flatMap { m ->
                val slices = Payload.split(Payload.build(m.recipients, m.text, m.group))
                m.outParts.asSequence().filter { it.acceptedAt == null }.map { p -> Pending(m, p, slices[p.no - 1]) }
            }
            .toList()

    fun markSent(messages: List<Message>, messageId: String, partNo: Int, now: Long): List<Message> =
        messages.map { m ->
            if (m.id != messageId) m
            else m.copy(outParts = m.outParts.map { p -> if (p.no == partNo) p.copy(sentAt = now) else p })
        }

    /** Exact correlation: only a part awaiting confirmation with this ybId is accepted. Returns null when nothing matched. */
    fun accept(messages: List<Message>, ybId: Int, now: Long): List<Message>? {
        var hit = false
        val out = messages.map { m ->
            if (hit || m.direction != Direction.OUT) return@map m
            val idx = m.outParts.indexOfFirst { it.ybId == ybId && it.acceptedAt == null }
            if (idx < 0) m
            else {
                hit = true
                m.copy(outParts = m.outParts.mapIndexed { i, p -> if (i == idx) p.copy(acceptedAt = now) else p })
            }
        }
        return if (hit) out else null
    }

    fun newMessage(
        id: String,
        recipients: List<String>,
        text: String,
        group: Boolean,
        existing: List<Message>,
        now: Long,
    ): Message {
        val rcpts = recipients.map(Payload::normaliseRecipient).filter { it.isNotEmpty() }
        val parts = Payload.split(Payload.build(rcpts, text, group))
        val ids = Ids.allocateYbIds(parts.size, Ids.usedYbIds(existing))
        return Message(
            id = id,
            direction = Direction.OUT,
            at = now,
            recipients = rcpts,
            group = group,
            text = text,
            outParts = parts.indices.map { OutPart(no = it + 1, ybId = ids[it]) },
            threadId = if (parts.size > 1) Ids.allocateThreadId(Ids.pendingThreadIds(existing)) else 0,
            totalParts = parts.size,
        )
    }
}
