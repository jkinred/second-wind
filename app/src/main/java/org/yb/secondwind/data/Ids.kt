package org.yb.secondwind.data

import kotlin.random.Random

/**
 * Part/thread id allocation. docs/PROTOCOL.md §5.2: never reuse a ybId that may
 * still be pending on the device; ids are 16-bit, thread ids 8-bit.
 */
object Ids {
    private const val YB_MAX = 0xFFFE // 0xFFFF reserved (device uses it for "unknown credit")

    /** Every ybId the phone has ever issued and still remembers; all are treated as unavailable. */
    fun usedYbIds(messages: List<Message>): Set<Int> =
        messages.asSequence().filter { it.direction == Direction.OUT }.flatMap { it.outParts.asSequence() }.map { it.ybId }.toHashSet()

    fun pendingThreadIds(messages: List<Message>): Set<Int> =
        messages.asSequence().filter { it.direction == Direction.OUT && it.outState != OutState.ACCEPTED }.map { it.threadId }.toHashSet()

    fun allocateYbIds(count: Int, used: Set<Int>, rng: Random = Random.Default): List<Int> {
        require(count in 1..(YB_MAX - used.size)) { "no free ybIds" }
        val out = ArrayList<Int>(count)
        while (out.size < count) {
            val id = 1 + rng.nextInt(YB_MAX) // 1..0xFFFE
            if (id !in used && id !in out) out += id
        }
        return out
    }

    fun allocateThreadId(pending: Set<Int>, rng: Random = Random.Default): Int {
        if (pending.size >= 255) return 1 + rng.nextInt(255)
        while (true) {
            val id = 1 + rng.nextInt(255)
            if (id !in pending) return id
        }
    }
}
