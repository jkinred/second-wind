package org.yb.secondwind.data

import org.yb.secondwind.proto.Payload

/**
 * One conversation. [identities] are contact primary addresses where a contact owns the
 * address, otherwise the raw address; two threads with the same identities are the same thread.
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

    /**
     * The address last used with [identity] in this thread, so a reply goes back over the
     * channel the other side last used; the contact's primary address when nothing has been exchanged.
     */
    fun lastAddressFor(identity: String, contacts: List<Contact>): String {
        val c = contacts.firstOrNull { it.address == identity } ?: return identity
        for (m in messages.asReversed()) m.counterparts.firstOrNull(c::owns)?.let { return it }
        return c.address
    }
}

object Threads {
    fun resolve(address: String, contacts: List<Contact>): String {
        val a = Payload.normaliseRecipient(address)
        return contacts.firstOrNull { it.owns(a) }?.address ?: a
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
