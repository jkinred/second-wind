package io.github.jkinred.secondwind.data

import io.github.jkinred.secondwind.proto.Payload

/** The selected address and its source; only received reply routes bypass telephone preparation. */
data class Destination(val address: String, val isReply: Boolean = false) {
    val preparedAddress: String? =
        if (isReply) Payload.normaliseRecipient(address) else Payload.prepareRecipient(address)
}

/**
 * One conversation. [identities] are contact primary addresses where a contact owns the
 * address, otherwise its recipient key; two threads with the same identities are the same thread.
 */
class Thread(
    val key: String,
    val identities: List<String>,
    val group: Boolean,
    val messages: List<Message>,
) {
    val latest: Message get() = messages.last()
    val unread: Int get() = messages.count { it.unread }
    val queued: Int get() = messages.count { it.direction == Direction.OUT && it.outState != OutState.ACCEPTED }

    fun title(contacts: List<Contact>): String {
        val names = identities.map { id -> contacts.firstOrNull { it.address == id }?.display ?: id }
        return buildList { addAll(names); if (group) add("My group") }.joinToString(", ")
    }
}

object Threads {
    fun resolve(address: String, contacts: List<Contact>): String =
        contacts.firstOrNull { it.owns(address) }?.address ?: Payload.recipientKey(address)

    /** Explicit selections win; otherwise keep the latest counterpart's address and source. */
    fun destinations(
        identities: List<String>,
        preferred: List<String>,
        contacts: List<Contact>,
        thread: Thread?,
    ): List<Destination> = identities.map { identity ->
        val resolved = resolve(identity, contacts)
        val selected = preferred.firstOrNull { resolve(it, contacts) == resolved }
        if (selected != null) {
            Destination(selected)
        } else {
            var previous: Destination? = null
            for (message in thread?.messages.orEmpty().asReversed()) {
                val address = when (message.direction) {
                    Direction.IN -> message.from.takeIf { resolve(it, contacts) == resolved }
                    Direction.OUT -> message.recipients.firstOrNull { resolve(it, contacts) == resolved }
                }
                if (address != null) {
                    previous = Destination(address, isReply = message.direction == Direction.IN)
                    break
                }
            }
            previous ?: Destination(identity)
        }
    }

    fun key(addresses: List<String>, group: Boolean, contacts: List<Contact>): String {
        val ids = addresses.map { resolve(it, contacts) }.filter { it.isNotEmpty() }.distinct().sorted()
        return (if (group) ids + Payload.GROUP else ids).joinToString(",")
    }

    /** Newest-first threads; messages within a thread oldest-first. */
    fun build(messages: List<Message>, contacts: List<Contact>): List<Thread> =
        messages.groupBy { it.threadKey(contacts) }.map { (key, ms) ->
            val sorted = ms.sortedBy { it.at }
            val sample = sorted.last()
            val identities = sample.counterparts.map { resolve(it, contacts) }.distinct()
            Thread(key, identities, sample.direction == Direction.OUT && sample.group, sorted)
        }.sortedByDescending { it.latest.at }

    /** Identities messaged or heard from, most recent first, excluding pinned contacts. */
    fun recents(messages: List<Message>, contacts: List<Contact>): List<Contact> {
        val seen = LinkedHashSet<String>()
        for (m in messages.sortedByDescending { it.at }) {
            for (a in m.counterparts) {
                val id = resolve(a, contacts)
                val c = contacts.firstOrNull { it.address == id }
                if (c?.pinned != true) seen += id
            }
        }
        return seen.map { id -> contacts.firstOrNull { it.address == id } ?: Contact(id) }
    }

    /**
     * Makes [absorb] (an identity) another address of [keep] (an identity). Either may already be a
     * contact; the surviving record keeps [keep]'s primary address, the first non-blank name, and is
     * pinned if either was.
     */
    fun merge(contacts: List<Contact>, keep: String, absorb: String): List<Contact> {
        if (keep == absorb) return contacts
        val target = contacts.firstOrNull { it.address == keep } ?: Contact(keep)
        val source = contacts.firstOrNull { it.address == absorb } ?: Contact(absorb)
        val merged = target.copy(
            name = target.name.ifBlank { source.name },
            pinned = target.pinned || source.pinned,
            aliases = (target.aliases + source.addresses).distinct(),
        )
        return contacts.filter { it.address != keep && it.address != absorb } + merged
    }

    /** Detaches [alias] from whichever contact owns it; it becomes its own identity again. */
    fun unlink(contacts: List<Contact>, alias: String): List<Contact> =
        contacts.map { if (alias in it.aliases) it.copy(aliases = it.aliases - alias) else it }
}
