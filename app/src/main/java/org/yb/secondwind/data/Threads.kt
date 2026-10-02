package org.yb.secondwind.data

import org.yb.secondwind.proto.Payload

class Thread(
    val key: String,
    val addresses: List<String>,
    val group: Boolean,
    val messages: List<Message>,
) {
    val latest: Message get() = messages.last()
    val unread: Int get() = messages.count { it.unread }
    val queued: Int get() = messages.count { it.direction == Direction.OUT && it.outState != OutState.ACCEPTED }

    fun title(contacts: List<Contact>): String {
        val names = addresses.map { a -> contacts.firstOrNull { it.address == a }?.display ?: a }
        return buildList { addAll(names); if (group) add("My group") }.joinToString(", ")
    }
}

object Threads {
    fun key(addresses: List<String>, group: Boolean): String {
        val a = addresses.map(Payload::normaliseRecipient).filter { it.isNotEmpty() }.sorted()
        return (if (group) a + Payload.GROUP else a).joinToString(",")
    }

    /** Newest-first threads; messages within a thread oldest-first. */
    fun build(messages: List<Message>): List<Thread> =
        messages.groupBy { it.threadKey }.map { (key, ms) ->
            val sorted = ms.sortedBy { it.at }
            val sample = sorted.last()
            val addresses = when (sample.direction) {
                Direction.IN -> listOf(Payload.normaliseRecipient(sample.from))
                Direction.OUT -> sample.recipients
            }
            Thread(key, addresses, sample.direction == Direction.OUT && sample.group, sorted)
        }.sortedByDescending { it.latest.at }

    /** Addresses messaged or heard from, most recent first, excluding those already in [contacts]. */
    fun recents(messages: List<Message>, contacts: List<Contact>): List<Contact> {
        val known = contacts.map { it.address }.toSet()
        val seen = LinkedHashMap<String, Long>()
        for (m in messages.sortedByDescending { it.at }) {
            val addrs = if (m.direction == Direction.IN) listOf(Payload.normaliseRecipient(m.from)) else m.recipients
            for (a in addrs) if (a.isNotEmpty() && a !in known && a !in seen) seen[a] = m.at
        }
        return seen.keys.map { Contact(it) }
    }
}
