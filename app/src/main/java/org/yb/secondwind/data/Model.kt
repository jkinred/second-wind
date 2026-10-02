package org.yb.secondwind.data

import kotlinx.serialization.Serializable
import org.yb.secondwind.proto.Mode
import org.yb.secondwind.proto.Payload

enum class Direction { IN, OUT }

/** Outbound: queued on phone → sending to YB → accepted by YB. Terminal; the device never reports more. */
enum class OutState { QUEUED, SENDING, ACCEPTED }

/**
 * One TEXT_OUT part. [ybId] is allocated once and never changes, so a retransmission
 * after a lost CONFIRMATION is idempotent on the device (docs/PROTOCOL.md §5.2).
 */
@Serializable
data class OutPart(
    val no: Int,
    val ybId: Int,
    val sentAt: Long? = null,
    val acceptedAt: Long? = null,
)

@Serializable
data class Message(
    val id: String,
    val direction: Direction,
    val at: Long,
    /** IN: sender address as received. */
    val from: String = "",
    /** OUT: normalised recipient addresses. */
    val recipients: List<String> = emptyList(),
    val group: Boolean = false,
    /** OUT: full text; IN: assembled lazily from [inParts]. */
    val text: String = "",
    /** OUT only. Payload slices are rebuilt deterministically from recipients/text/group. */
    val outParts: List<OutPart> = emptyList(),
    /** OUT: 1-byte id shared by all parts; IN: device thread id used for reassembly. */
    val threadId: Int = 0,
    /** IN only: part number → text. */
    val inParts: Map<Int, String> = emptyMap(),
    val totalParts: Int = 1,
    val unread: Boolean = false,
    /** IN only: credit balance the device reported alongside this message, if any. */
    val creditAfter: Int? = null,
) {
    val body: String
        get() = when (direction) {
            Direction.OUT -> text
            Direction.IN -> {
                if (inParts.size == 1 && inParts.containsKey(1)) inParts.getValue(1)
                else (1..maxOf(totalParts, inParts.keys.maxOrNull() ?: 0)).joinToString("") { inParts[it] ?: "\n[part $it missing]\n" }
            }
        }

    val complete: Boolean get() = direction == Direction.OUT || inParts.size >= totalParts

    val outState: OutState
        get() = when {
            direction == Direction.IN -> OutState.ACCEPTED
            outParts.isNotEmpty() && outParts.all { it.acceptedAt != null } -> OutState.ACCEPTED
            outParts.any { it.sentAt != null } -> OutState.SENDING
            else -> OutState.QUEUED
        }

    val acceptedParts: Int get() = outParts.count { it.acceptedAt != null }

    /** Raw normalised addresses on the other end: sender for IN, recipients for OUT. */
    val counterparts: List<String>
        get() = when (direction) {
            Direction.IN -> listOf(Payload.normaliseRecipient(from)).filter { it.isNotEmpty() }
            Direction.OUT -> recipients
        }

    /** Conversation identity: counterparts resolved through [contacts] so one person's phone and e-mail share a thread. */
    fun threadKey(contacts: List<Contact>): String = Threads.key(counterparts, direction == Direction.OUT && group, contacts)
}

/**
 * A person. [address] is the primary address and the stable identity used in thread keys;
 * [aliases] are other addresses (phone, e-mail) the same person uses, merged by the user.
 */
@Serializable
data class Contact(
    val address: String,
    val name: String = "",
    /** Explicitly starred by the user (vs. merely recent). */
    val pinned: Boolean = false,
    val aliases: List<String> = emptyList(),
) {
    val isGroup get() = address == Payload.GROUP
    val display get() = name.ifBlank { if (isGroup) "My group" else address }
    val addresses get() = listOf(address) + aliases
    fun owns(a: String) = a == address || a in aliases
}

@Serializable
data class Settings(
    val keyword: String = "",
    val password: String = "",
    val deviceAddress: String = "",
    val deviceName: String = "",
    val mode: Mode = Mode.STAND_ALONE,
    /** Set when creds/mode/device change; the next session re-sends the mode command. */
    val modeDirty: Boolean = true,
) {
    val credentialsValid get() = keyword.length == 4 && password.length == 4
}

/** Last STATUS seen, persisted so the device panel is informative while disconnected. */
@Serializable
data class DeviceInfo(
    val firmware: String = "",
    val storageNearlyFull: Boolean? = null,
    val trackingInterval: String? = null,
    val inboxCheckInterval: String? = null,
    val at: Long = 0,
)

@Serializable
data class AppData(
    val settings: Settings = Settings(),
    val messages: List<Message> = emptyList(),
    val contacts: List<Contact> = emptyList(),
    val credit: Int? = null,
    val creditAt: Long = 0,
    val lastConnectedAt: Long = 0,
    val lastConnectRung: String = "",
    /** Last time a REQUEST was answered (NO_TEXT or TEXT_IN). */
    val lastPullAt: Long = 0,
    val device: DeviceInfo? = null,
)
