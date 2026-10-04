package io.github.jkinred.secondwind.data

import io.github.jkinred.secondwind.proto.Payload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OutboxTest {
    private val long = "x".repeat(305 + 321 + 5) // 3 parts

    @Test fun newMessageAllocatesDistinctIdsAvoidingExisting() {
        val first = Outbox.newMessage("a", listOf("a@b.c"), long, false, emptyList(), 1)
        assertEquals(3, first.outParts.size)
        assertEquals(3, first.outParts.map { it.ybId }.toSet().size)
        assertTrue(first.threadId in 1..255)
        val second = Outbox.newMessage("b", listOf("a@b.c"), long, false, listOf(first), 2)
        assertTrue(second.outParts.none { it.ybId in first.outParts.map { p -> p.ybId } })
        assertTrue(second.threadId != first.threadId)
    }

    @Test fun pendingKeepsIdsStableAndSkipsAcceptedParts() {
        val m = Outbox.newMessage("a", listOf("a@b.c"), long, false, emptyList(), 1)
        val before = Outbox.pending(listOf(m))
        assertEquals(listOf(1, 2, 3), before.map { it.part.no })
        assertEquals(listOf(305, 321, 5 + "a@b.c:".length), before.map { it.payload.length })

        val accepted = Outbox.accept(listOf(m), before[1].part.ybId, 10)!!
        val after = Outbox.pending(accepted)
        assertEquals(listOf(1, 3), after.map { it.part.no })
        assertEquals(before[0].part.ybId, after[0].part.ybId)
        assertEquals(OutState.QUEUED, accepted.single().outState) // nothing sent yet
    }

    @Test fun acceptIsExactAndSingleShot() {
        val m = Outbox.newMessage("a", listOf("a@b.c"), "hi", false, emptyList(), 1)
        val id = m.outParts.single().ybId
        assertNull(Outbox.accept(listOf(m), id xor 1, 5))
        val once = Outbox.accept(listOf(m), id, 5)
        assertNotNull(once)
        assertEquals(OutState.ACCEPTED, once!!.single().outState)
        assertNull(Outbox.accept(once, id, 6)) // duplicate CONFIRMATION ignored
    }

    @Test fun stateTransitions() {
        val m = Outbox.newMessage("a", listOf("a@b.c"), "hi", false, emptyList(), 1)
        assertEquals(OutState.QUEUED, m.outState)
        val sent = Outbox.markSent(listOf(m), "a", 1, 2).single()
        assertEquals(OutState.SENDING, sent.outState)
        assertEquals(OutState.ACCEPTED, Outbox.accept(listOf(sent), m.outParts[0].ybId, 3)!!.single().outState)
    }

    @Test fun preparedBoundaryMessageLeavesHistoricMultipartUntouched() {
        val formatted = "+61 400-000-000"
        val body = "x".repeat(295)
        val historic = Message(
            id = "old", direction = Direction.OUT, at = 1,
            recipients = listOf(formatted), text = body,
            outParts = listOf(
                OutPart(1, 17, sentAt = 2, acceptedAt = 3),
                OutPart(2, 18, sentAt = 4),
            ),
            threadId = 23, totalParts = 2,
        )
        val contacts = listOf(Contact("61400000000", "Sarah"))
        val thread = Threads.build(listOf(historic), contacts).single()
        val prepared = Threads.destinations(thread.identities, emptyList(), contacts, thread)
            .map { requireNotNull(it.preparedAddress) }
        val estimate = Payload.estimate(prepared, body, false)
        assertEquals(307, estimate.chars)
        assertEquals(1, estimate.parts)

        val fresh = Outbox.newMessage("new", prepared, body, false, listOf(historic), 5)
        assertEquals(listOf("61400000000"), fresh.recipients)
        assertEquals(1, fresh.totalParts)
        assertEquals(1, fresh.outParts.size)
        assertEquals(0, fresh.threadId)
        assertTrue(fresh.outParts.single().ybId !in listOf(17, 18))

        assertEquals(
            listOf(formatted + ":" + "x".repeat(289), "x".repeat(6)),
            Payload.split(Payload.build(historic.recipients, historic.text, historic.group)),
        )
        val pending = Outbox.pending(listOf(historic, fresh))
        assertEquals(listOf("old", "new"), pending.map { it.message.id })
        assertEquals(OutPart(2, 18, sentAt = 4), pending[0].part)
        assertEquals("x".repeat(6), pending[0].payload)
        assertEquals(historic, pending[0].message)
        assertEquals(23, pending[0].message.threadId)
        assertEquals(1, pending[0].message.acceptedParts)
        assertEquals(OutState.SENDING, pending[0].message.outState)
        assertEquals("61400000000:$body", pending[1].payload)
    }
}

class InboxTest {
    @Test fun reassemblesOutOfOrderParts() {
        var ms = Inbox.receive(emptyList(), 10, 2, 2, 7, "", "world", null, 1)
        assertEquals(1, ms.size)
        assertTrue(!ms[0].complete)
        ms = Inbox.receive(ms, 11, 2, 1, 7, "bob@x.com", "hello ", 42, 2)
        val m = ms.single()
        assertTrue(m.complete)
        assertEquals("bob@x.com", m.from)
        assertEquals("hello world", m.body)
        assertTrue(m.unread)
        assertEquals(42, m.creditAfter)
    }

    @Test fun completedThreadIdDoesNotAbsorbNewMessage() {
        var ms = Inbox.receive(emptyList(), 1, 1, 1, 0, "a@x", "one", null, 1)
        ms = Inbox.receive(ms, 2, 2, 1, 3, "b@x", "two-", null, 2)
        ms = Inbox.receive(ms, 3, 2, 2, 3, "", "part", null, 3)
        ms = Inbox.receive(ms, 4, 2, 1, 3, "c@x", "reused thread id", null, 4)
        assertEquals(3, ms.size)
        assertEquals("two-part", ms[1].body)
        assertEquals("c@x", ms[2].from)
    }
}

class ThreadsTest {
    @Test fun groupsByCounterpartRegardlessOfDirection() {
        val out = Outbox.newMessage("o", listOf("Bob@X.com"), "hi", false, emptyList(), 1)
        val inb = Inbox.receive(emptyList(), 1, 1, 1, 0, "bob@x.com", "yo", null, 2).single()
        val other = Outbox.newMessage("g", listOf("bob@x.com"), "all", true, emptyList(), 3)
        val threads = Threads.build(listOf(out, inb, other), emptyList())
        assertEquals(2, threads.size)
        assertEquals("bob@x.com,g", threads[0].key) // newest first
        assertEquals(listOf("o", inb.id), threads[1].messages.map { it.id })
        assertEquals(1, threads[1].unread)
        assertEquals(1, threads[1].queued)
    }

    @Test fun recentsExcludeContactsAndDedupe() {
        val a = Outbox.newMessage("1", listOf("a@x"), "t", false, emptyList(), 1)
        val b = Outbox.newMessage("2", listOf("b@x", "a@x"), "t", false, emptyList(), 2)
        val c = Inbox.receive(emptyList(), 1, 1, 1, 0, "c@x", "t", null, 3).single()
        val r = Threads.recents(listOf(a, b, c), listOf(Contact("b@x", "Bee", pinned = true)))
        assertEquals(listOf("c@x", "a@x"), r.map { it.address })
    }

    @Test fun mergedContactCollapsesPhoneAndEmailThreadsAndRepliesOnLastChannel() {
        val toPhone = Outbox.newMessage("1", listOf("+61400000000"), "hi", false, emptyList(), 1)
        val fromEmail = Inbox.receive(emptyList(), 1, 1, 1, 0, "sarah@x.com", "hello", null, 2).single()
        assertEquals(2, Threads.build(listOf(toPhone, fromEmail), emptyList()).size)

        val contacts = Threads.merge(listOf(Contact("+61400000000", "Sarah", pinned = true)), keep = "+61400000000", absorb = "sarah@x.com")
        val merged = contacts.single()
        assertEquals(listOf("+61400000000", "sarah@x.com"), merged.addresses)
        assertTrue(merged.pinned)

        val t = Threads.build(listOf(toPhone, fromEmail), contacts).single()
        assertEquals(listOf("+61400000000"), t.identities)
        assertEquals("Sarah", t.title(contacts))
        assertEquals(
            listOf(Destination("sarah@x.com", isReply = true)),
            Threads.destinations(t.identities, emptyList(), contacts, t),
        )
        assertTrue(Threads.recents(listOf(toPhone, fromEmail), contacts).isEmpty())

        val unlinked = Threads.unlink(contacts, "sarah@x.com")
        assertEquals(2, Threads.build(listOf(toPhone, fromEmail), unlinked).size)
    }

    @Test fun formattedPhonePrimaryAndAliasKeepStoredIdentityAndName() {
        val contact = Contact(
            "+61 400-000-000", "Sarah", pinned = true,
            aliases = listOf("sarah+boat@x.com", "+61 400-000-001"),
        )
        val contacts = listOf(contact)
        val outgoing = Outbox.newMessage("out", listOf("61400000000"), "hi", false, emptyList(), 1)
        val incoming = Inbox.receive(emptyList(), 1, 1, 1, 0, "+61\t400 000-001", "hello", null, 2).single()
        assertTrue(contact.owns("61400000000"))
        assertTrue(contact.owns("61400000001"))
        assertEquals(contact.address, Threads.resolve("+61 400 000 000", contacts))
        assertEquals(contact.address, Threads.resolve("61400000001", contacts))

        val thread = Threads.build(listOf(incoming, outgoing), contacts).single()
        assertEquals(contact.address, thread.key)
        assertEquals(listOf(contact.address), thread.identities)
        assertEquals(listOf(outgoing, incoming), thread.messages)
        assertEquals("Sarah", thread.title(contacts))
        assertTrue(Threads.recents(listOf(outgoing, incoming), contacts).isEmpty())
        assertEquals("+61 400-000-000", contact.address)
        assertEquals(listOf("sarah+boat@x.com", "+61 400-000-001"), contact.aliases)
        assertEquals("+61\t400 000-001", incoming.from)
        assertEquals(listOf("61400000000"), outgoing.recipients)
    }

    @Test fun rawPhoneHistorySharesOneThreadWithoutAContact() {
        val outgoing = Outbox.newMessage("out", listOf("+61 400-000-000"), "hi", false, emptyList(), 1)
        val incoming = Inbox.receive(emptyList(), 1, 1, 1, 0, "61400000000", "hello", null, 2).single()
        val thread = Threads.build(listOf(outgoing, incoming), emptyList()).single()
        assertEquals("61400000000", thread.key)
        assertEquals(listOf("61400000000"), thread.identities)
        assertEquals(listOf(outgoing, incoming), thread.messages)
        assertEquals(listOf("61400000000"), Threads.recents(listOf(outgoing, incoming), emptyList()).map { it.address })
        assertEquals(
            listOf(Destination("61400000000", isReply = true)),
            Threads.destinations(thread.identities, emptyList(), emptyList(), thread),
        )
        assertEquals(listOf("+61 400-000-000"), outgoing.recipients)
    }

    @Test fun emailPlusTagIsRetainedForIdentityAndNewDestination() {
        val contact = Contact("sarah+boat@x.com", "Sarah")
        assertTrue(contact.owns(" Sarah+Boat@X.COM "))
        assertEquals(contact.address, Threads.resolve("Sarah+Boat@X.COM", listOf(contact)))
        assertEquals("sarah@x.com", Threads.resolve("Sarah@X.COM", listOf(contact)))
        val destination = Threads.destinations(listOf(" Sarah+Boat@X.COM "), emptyList(), listOf(contact), null).single()
        assertEquals("sarah+boat@x.com", destination.preparedAddress)
        assertEquals(false, destination.isReply)
    }

    @Test fun explicitSelectionsWinForEveryIdentityRegardlessOfPreferredOrder() {
        val contacts = listOf(
            Contact("+61 400-000-000", "Alice", aliases = listOf("alice@x.com")),
            Contact("bob@x.com", "Bob", aliases = listOf("+61 400-000-001")),
        )
        val previous = Outbox.newMessage("out", listOf("alice@x.com", "+61 400-000-001"), "hi", false, emptyList(), 1)
        val thread = Threads.build(listOf(previous), contacts).single()
        val destinations = Threads.destinations(
            thread.identities, listOf("unrelated@x.com", "bob@x.com", "+61400000000"), contacts, thread,
        )
        assertEquals(listOf(Destination("+61400000000"), Destination("bob@x.com")), destinations)
        assertEquals(listOf("61400000000", "bob@x.com"), destinations.map { it.preparedAddress })
    }

    @Test fun receivedRouteKeepsRawSenderAndBypassesNewPhoneValidation() {
        val sender = " 0400 000-000 "
        val contact = Contact("sarah@x.com", "Sarah", aliases = listOf("0400 000-000"))
        val contacts = listOf(contact)
        val incoming = Inbox.receive(emptyList(), 1, 1, 1, 0, sender, "hello", null, 1).single()
        val thread = Threads.build(listOf(incoming), contacts).single()
        val reply = Threads.destinations(thread.identities, emptyList(), contacts, thread).single()
        assertEquals(sender, reply.address)
        assertTrue(reply.isReply)
        assertEquals("0400 000-000", reply.preparedAddress)
        assertEquals(sender, incoming.from)

        val selected = Threads.destinations(thread.identities, listOf(sender), contacts, thread).single()
        assertEquals(sender, selected.address)
        assertEquals(false, selected.isReply)
        assertNull(selected.preparedAddress)
    }

    @Test fun latestOutgoingAddressIsPreparedAsANewPhoneDestination() {
        val incoming = Inbox.receive(emptyList(), 1, 1, 1, 0, "+61400000000", "hello", null, 1).single()
        val outgoing = Outbox.newMessage("out", listOf("+61 400-000-000"), "hi", false, emptyList(), 2)
        val thread = Threads.build(listOf(outgoing, incoming), emptyList()).single()
        val destination = Threads.destinations(thread.identities, emptyList(), emptyList(), thread).single()
        assertEquals("+61 400-000-000", destination.address)
        assertEquals(false, destination.isReply)
        assertEquals("61400000000", destination.preparedAddress)
        assertEquals(listOf("+61 400-000-000"), outgoing.recipients)
    }

    @Test fun invalidIdentityWithoutHistoryCannotBecomeANewDestination() {
        val destination = Threads.destinations(listOf("0400 000-000"), emptyList(), emptyList(), null).single()
        assertEquals("0400 000-000", destination.address)
        assertEquals(false, destination.isReply)
        assertNull(destination.preparedAddress)
    }

    @Test fun titleResolvesContactNames() {
        val t = Threads.build(listOf(Outbox.newMessage("1", listOf("a@x"), "t", true, emptyList(), 1)), emptyList()).single()
        assertEquals("Alice, My group", t.title(listOf(Contact("a@x", "Alice"))))
    }
}
