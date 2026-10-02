package io.github.jkinred.secondwind.data

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
        assertEquals("sarah@x.com", t.lastAddressFor("+61400000000", contacts)) // last exchange was e-mail
        assertTrue(Threads.recents(listOf(toPhone, fromEmail), contacts).isEmpty())

        val unlinked = Threads.unlink(contacts, "sarah@x.com")
        assertEquals(2, Threads.build(listOf(toPhone, fromEmail), unlinked).size)
    }

    @Test fun titleResolvesContactNames() {
        val t = Threads.build(listOf(Outbox.newMessage("1", listOf("a@x"), "t", true, emptyList(), 1)), emptyList()).single()
        assertEquals("Alice, My group", t.title(listOf(Contact("a@x", "Alice"))))
    }
}
