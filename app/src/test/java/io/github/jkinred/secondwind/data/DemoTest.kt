package io.github.jkinred.secondwind.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoTest {
    private val now = 1_800_000_000_000L
    private val d = Demo.data(AppData(settings = Settings(keyword = "ABCD", password = "1234", deviceAddress = "00:06:66:00:00:01", deviceName = "YB")), now)
    private val threads = Threads.build(d.messages, d.contacts)

    @Test fun tenThreadsSevenSingletonsThreeConversations() {
        assertEquals(10, threads.size)
        assertEquals(7, threads.count { it.messages.size == 1 })
        assertEquals(3, threads.count { it.messages.size >= 5 })
        assertEquals(d.messages.size, threads.sumOf { it.messages.size })
    }

    @Test fun everyThreadResolvesToNamedContactAndIsSettled() {
        threads.forEach { t ->
            assertTrue(t.title(d.contacts), d.contacts.any { it.address == t.identities.single() && it.name.isNotBlank() })
            assertEquals(0, t.queued)
            assertTrue(t.messages.all { it.complete && it.at < now })
        }
        assertEquals(2, threads.sumOf { it.unread })
        assertEquals(d.messages.size, d.messages.map { it.id }.toSet().size)
        assertEquals(Ids.usedYbIds(d.messages).size, d.messages.sumOf { it.outParts.size })
    }

    @Test fun keepsRealSettings() {
        assertEquals("ABCD", d.settings.keyword)
        assertEquals("YB", d.settings.deviceName)
    }
}
