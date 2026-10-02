package io.github.jkinred.secondwind

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.github.jkinred.secondwind.bt.Bluetooth
import io.github.jkinred.secondwind.bt.ConnectError
import io.github.jkinred.secondwind.bt.DeviceLink
import io.github.jkinred.secondwind.data.AppData
import io.github.jkinred.secondwind.data.Contact
import io.github.jkinred.secondwind.data.DeviceInfo
import io.github.jkinred.secondwind.data.Direction
import io.github.jkinred.secondwind.data.Ids
import io.github.jkinred.secondwind.data.Inbox
import io.github.jkinred.secondwind.data.OutState
import io.github.jkinred.secondwind.data.Threads
import io.github.jkinred.secondwind.data.Outbox
import io.github.jkinred.secondwind.data.Settings
import io.github.jkinred.secondwind.data.Store
import io.github.jkinred.secondwind.proto.Credentials
import io.github.jkinred.secondwind.proto.Frame
import io.github.jkinred.secondwind.proto.Inbound
import io.github.jkinred.secondwind.proto.Keyword
import io.github.jkinred.secondwind.proto.Mode
import io.github.jkinred.secondwind.proto.Outbound
import io.github.jkinred.secondwind.proto.Payload
import io.github.jkinred.secondwind.proto.TextOutPart
import io.github.jkinred.secondwind.proto.Type
import io.github.jkinred.secondwind.proto.toHex
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Link { DISCONNECTED, CONNECTING, CONNECTED }

/** Known failure conditions; the UI owns the copy and the remedy. Raw detail goes to the log. */
sealed interface Problem {
    data object PermissionNeeded : Problem
    data object BluetoothOff : Problem
    data object NoDevice : Problem
    data object CredentialsMissing : Problem
    data object CredentialsRejected : Problem
    class Unreachable(val detail: String) : Problem
    class StaleBond(val detail: String) : Problem
    class RepairFailed(val detail: String) : Problem
    class Dropped(val detail: String) : Problem
}

data class UiState(
    val data: AppData = AppData(),
    val link: Link = Link.DISCONNECTED,
    val problem: Problem? = null,
    /** True while a REQUEST/mailbox check is in flight on user demand. */
    val pulling: Boolean = false,
    val log: List<String> = emptyList(),
)

class Messenger(app: Application) : AndroidViewModel(app) {
    private val store = Store(app)
    val bluetooth = Bluetooth(app)

    private val _state = MutableStateFlow(UiState(data = store.load()))
    val state: StateFlow<UiState> = _state

    private var link: DeviceLink? = null
    private var session: Job? = null
    private var pullJob: Job? = null
    private val txLock = Mutex()
    private val flushLock = Mutex()
    private var lastTxAt = 0L
    private var statusSeenThisSession = false

    private val data get() = _state.value.data
    private val settings get() = data.settings
    private val keyword get() = Keyword.of(settings.keyword)
    private val credentials get() = Credentials.of(settings.keyword, settings.password)

    // ---- settings / contacts ----------------------------------------------

    fun saveSettings(s: Settings) {
        val old = settings
        val dirty = old.modeDirty || s.keyword != old.keyword || s.password != old.password ||
            s.mode != old.mode || s.deviceAddress != old.deviceAddress
        val deviceChanged = s.deviceAddress != old.deviceAddress
        update {
            copy(
                settings = s.copy(modeDirty = dirty),
                credit = if (dirty) null else credit,
                device = if (deviceChanged) null else device,
            )
        }
        if (dirty && _state.value.link == Link.CONNECTED && s.credentialsValid) viewModelScope.launch { sendMode(s.mode) }
    }

    fun pinContact(address: String, name: String = "") = update {
        val a = Payload.normaliseRecipient(address)
        val existing = contacts.firstOrNull { it.owns(a) }
        val c = (existing ?: Contact(a)).copy(name = name.ifBlank { existing?.name ?: "" }, pinned = true)
        copy(contacts = contacts.filter { it !== existing } + c)
    }

    /** Un-stars; a contact that still links several addresses is kept (unpinned) so threads stay merged. */
    fun unpinContact(address: String) = update {
        copy(contacts = contacts.mapNotNull { if (it.address != address) it else if (it.aliases.isEmpty()) null else it.copy(pinned = false) })
    }

    fun mergeContacts(keep: String, absorb: String) {
        update { copy(contacts = Threads.merge(contacts, keep, absorb)) }
        log("merged $absorb into $keep")
    }

    fun unlinkAlias(alias: String) = update { copy(contacts = Threads.unlink(contacts, alias)) }

    fun renameContact(address: String, name: String) = update { copy(contacts = contacts.map { if (it.address == address) it.copy(name = name) else it }) }

    fun dismissProblem() = _state.update { it.copy(problem = null) }

    // ---- connection -------------------------------------------------------

    fun connect(hasPermission: Boolean) {
        if (_state.value.link != Link.DISCONNECTED) return
        val s = settings
        val problem = when {
            !hasPermission -> Problem.PermissionNeeded
            !bluetooth.isEnabled -> Problem.BluetoothOff
            s.deviceAddress.isEmpty() || bluetooth.device(s.deviceAddress) == null -> Problem.NoDevice
            !s.credentialsValid -> Problem.CredentialsMissing
            else -> null
        }
        if (problem != null) { _state.update { it.copy(problem = problem) }; return }
        val device = bluetooth.device(s.deviceAddress)!!
        bluetooth.cancelDiscovery()
        _state.update { it.copy(link = Link.CONNECTING, problem = null) }
        statusSeenThisSession = false
        session = viewModelScope.launch {
            val l = try {
                DeviceLink.connect(device, ::log)
            } catch (e: Exception) {
                log("connect failed: ${e.message}")
                val p = when (e) {
                    is ConnectError.StaleBondNotRemovable -> Problem.StaleBond(e.message ?: "")
                    is ConnectError.RepairFailed -> Problem.RepairFailed(e.message ?: "")
                    else -> Problem.Unreachable(e.message ?: "")
                }
                _state.update { it.copy(link = Link.DISCONNECTED, problem = p) }
                return@launch
            }
            link = l
            val now = System.currentTimeMillis()
            update { copy(lastConnectedAt = now, lastConnectRung = l.rung) }
            _state.update { it.copy(link = Link.CONNECTED) }
            log("connected ${s.deviceName} via ${l.rung}" + if (l.repaired) " after re-pairing" else "")

            val reader = launch { l.readLoop() }
            var dropped: String? = null
            val handler = launch {
                try {
                    for (f in l.frames) onFrame(f)
                } catch (e: Exception) {
                    dropped = e.message ?: "connection closed"
                    log("link closed: $dropped")
                }
            }
            val periodic = launch {
                // Primer: the device may discard the first frame after connect; make it one whose reply is optional.
                tx(Outbound.statusRequest(keyword))
                delay(1500)
                if (settings.modeDirty) sendMode(settings.mode)
                delay(2000)
                flushOutbox()
                while (isActive) {
                    if (!statusSeenThisSession) tx(Outbound.statusRequest(keyword))
                    tx(Outbound.request(keyword))
                    delay(30_000)
                }
            }
            reader.join()
            periodic.cancel(); handler.cancel(); pullJob?.cancel()
            link = null
            _state.update {
                it.copy(
                    link = Link.DISCONNECTED,
                    pulling = false,
                    problem = it.problem ?: dropped?.let { d -> Problem.Dropped(d) },
                )
            }
            log("disconnected")
        }
    }

    fun disconnect() {
        link?.close()
        session?.cancel()
        pullJob?.cancel()
        link = null
        _state.update { it.copy(link = Link.DISCONNECTED, pulling = false) }
    }

    /** Troubleshooting: drop the phone-side bond so the next connect pairs afresh. */
    fun forgetBond(): Boolean {
        disconnect()
        val ok = bluetooth.forgetBond(settings.deviceAddress)
        log(if (ok) "bond removed" else "bond removal refused by platform")
        return ok
    }

    override fun onCleared() = disconnect()

    // ---- outbound ---------------------------------------------------------

    fun queue(recipients: List<String>, text: String, group: Boolean) {
        val now = System.currentTimeMillis()
        update { copy(messages = messages + Outbox.newMessage("out-$now", recipients, text, group, messages, now)) }
        log("queued out-$now to ${(recipients + if (group) listOf("group") else emptyList()).joinToString(",")} (${text.length} chars)")
        if (_state.value.link == Link.CONNECTED) viewModelScope.launch { flushOutbox() }
    }

    /** Removes a message from the phone. A SENDING/ACCEPTED message cannot be recalled from the device. */
    fun delete(id: String) = update { copy(messages = messages.filter { it.id != id }) }

    fun markRead(threadKey: String) = update {
        if (messages.none { it.unread && it.threadKey(contacts) == threadKey }) this
        else copy(messages = messages.map { if (it.unread && it.threadKey(contacts) == threadKey) it.copy(unread = false) else it })
    }

    private suspend fun flushOutbox() = flushLock.withLock {
        val l = link ?: return
        val creds = credentials
        for (p in Outbox.pending(data.messages)) {
            if (link !== l) return
            val m = p.message
            tx(
                Outbound.textOut(
                    creds,
                    TextOutPart(
                        ybId = p.part.ybId, payload = p.payload,
                        totalParts = m.totalParts, partNo = p.part.no, threadId = m.threadId,
                        includePosition = p.part.no == 1,
                    ),
                ),
            )
            val now = System.currentTimeMillis()
            update { copy(messages = Outbox.markSent(messages, m.id, p.part.no, now)) }
            log("sent ${m.id} part ${p.part.no}/${m.totalParts} ybId=${p.part.ybId}")
            delay(3000)
        }
    }

    private suspend fun sendMode(mode: Mode) {
        val ybId = Ids.allocateYbIds(1, Ids.usedYbIds(data.messages)).single()
        tx(Outbound.setMode(credentials, mode, ybId))
        update { copy(settings = settings.copy(modeDirty = false)) }
        log("mode set ${mode.name}")
    }

    /** Stand-alone: REQUEST. Hotspot: trigger a satellite mailbox poll (costs credits), then REQUEST. */
    fun pullNow() {
        if (_state.value.link != Link.CONNECTED || pullJob?.isActive == true) return
        pullJob = viewModelScope.launch {
            _state.update { it.copy(pulling = true) }
            try {
                when (settings.mode) {
                    Mode.STAND_ALONE -> { tx(Outbound.request(keyword)); delay(3000) }
                    Mode.HOTSPOT -> {
                        log("hotspot mailbox check")
                        val ybId = Ids.allocateYbIds(1, Ids.usedYbIds(data.messages)).single()
                        tx(Outbound.textOut(credentials, TextOutPart(ybId, "", totalParts = 0, partNo = 0, includePosition = false)))
                        delay(120_000)
                        tx(Outbound.mailboxCheck(keyword))
                        delay(30_000)
                        tx(Outbound.request(keyword))
                        delay(3000)
                    }
                }
            } finally {
                _state.update { it.copy(pulling = false) }
            }
        }
    }

    private suspend fun tx(bytes: ByteArray) {
        val l = link ?: return
        txLock.withLock {
            val wait = lastTxAt + 1000 - System.currentTimeMillis()
            if (wait > 0) delay(wait)
            try {
                l.send(bytes)
                log("> ${Type.name(bytes[9].toInt() and 0xFF)}")
            } catch (e: Exception) {
                log("send failed: ${e.message}")
            }
            lastTxAt = System.currentTimeMillis()
        }
    }

    // ---- inbound ----------------------------------------------------------

    private suspend fun onFrame(f: Frame) {
        if (!f.checksumOk) { log("< ${Type.name(f.type)} bad checksum ${f.body.toHex()}"); return }
        val now = System.currentTimeMillis()
        when (val m = Inbound.parse(f)) {
            is Inbound.Confirmation -> {
                val updated = Outbox.accept(data.messages, m.ybId, now)
                if (updated == null) log("< CONFIRMATION ybId=${m.ybId} (no pending part; ignored)")
                else { update { copy(messages = updated) }; log("< CONFIRMATION ybId=${m.ybId}") }
            }
            is Inbound.NoText -> { update { copy(lastPullAt = now) }; log("< NO_TEXT") }
            is Inbound.Status -> {
                statusSeenThisSession = true
                val s = m.status
                update { copy(device = DeviceInfo(s.firmware, s.storageNearlyFull, s.trackingInterval, s.inboxCheckInterval, now)) }
                log("< STATUS fw=${s.firmware} storageHigh=${s.storageNearlyFull} tracking=${s.trackingInterval} inbox=${s.inboxCheckInterval}")
            }
            is Inbound.InvalidCredentials -> {
                tx(Outbound.ack(keyword, m.ybId))
                log("< TEXT_IN short body: credentials rejected")
                _state.update { it.copy(problem = Problem.CredentialsRejected) }
                delay(1500)
                disconnect()
            }
            is Inbound.Text -> {
                tx(Outbound.ack(keyword, m.ybId))
                val before = data.messages
                update {
                    copy(
                        messages = Inbox.receive(messages, m.ybId, m.totalParts, m.partNo, m.threadId, m.sender, m.text, m.credit, now),
                        credit = m.credit ?: credit,
                        creditAt = if (m.credit != null) now else creditAt,
                        lastPullAt = now,
                    )
                }
                log("< TEXT_IN ybId=${m.ybId} part ${m.partNo}/${m.totalParts} from=${m.sender} credit=${m.credit}")
                data.messages.firstOrNull { it.direction == Direction.IN && it.complete && before.none { b -> b.id == it.id && b.complete } }
                    ?.let { log("received ${it.id} from ${it.from} (${it.body.length} chars, ${it.totalParts} part${if (it.totalParts == 1) "" else "s"})") }
                // The device hands over one message per REQUEST; ask again without waiting for the 30 s tick.
                delay(500)
                tx(Outbound.request(keyword))
            }
            is Inbound.Unknown -> log("< ${Type.name(m.type)} ${m.body.toHex()} (ignored)")
        }
    }

    // ---- persistence / log ------------------------------------------------

    private fun update(fn: AppData.() -> AppData) {
        val d = _state.updateAndGet { it.copy(data = it.data.fn()) }.data
        viewModelScope.launch(Dispatchers.IO) { store.save(d) }
    }

    private val ts = SimpleDateFormat("HH:mm:ss", Locale.US)

    private fun log(line: String) {
        val entry = "${ts.format(Date())} $line"
        android.util.Log.d("SecondWind", line)
        _state.update { it.copy(log = (it.log + entry).takeLast(400)) }
    }

    fun exportLog(): String = _state.value.log.joinToString("\n")

    /** Convenience for UI: threads newest first. */
    val hasQueued: Boolean get() = data.messages.any { it.direction == Direction.OUT && it.outState != OutState.ACCEPTED }
}

private fun <T> MutableStateFlow<T>.updateAndGet(fn: (T) -> T): T {
    while (true) {
        val prev = value
        val next = fn(prev)
        if (compareAndSet(prev, next)) return next
    }
}
