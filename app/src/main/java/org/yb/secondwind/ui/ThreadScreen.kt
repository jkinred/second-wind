package org.yb.secondwind.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Call
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.yb.secondwind.UiState
import org.yb.secondwind.data.Contact
import org.yb.secondwind.data.Direction
import org.yb.secondwind.data.Message
import org.yb.secondwind.data.OutState
import org.yb.secondwind.data.Thread
import org.yb.secondwind.proto.Payload

const val ACCEPTED_EXPLANATION =
    "The YB accepted every part of this message and queued for transmission, which occurs when connected to satellite."

/** Channel glyph for an address: phone → SMS, anything else → e-mail. */
fun channelIcon(address: String): ImageVector = if (Payload.isPhone(address)) Icons.Default.Call else Icons.Default.Email
fun channelName(address: String): String = if (Payload.isPhone(address)) "SMS" else "e-mail"

/**
 * A conversation with one identity set. Works for a brand-new thread (no messages yet).
 * [identities] are contact primary addresses or raw addresses; the address actually sent to is
 * chosen per identity (last channel used, switchable when the contact has several).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    state: UiState,
    identities: List<String>,
    /** Addresses the user explicitly picked (new message); chooses the initial channel for merged contacts. */
    preferred: List<String>,
    group: Boolean,
    thread: Thread?,
    otherThreads: List<Thread>,
    bluetoothOn: Boolean,
    onBack: () -> Unit,
    onStatus: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onQueue: (addresses: List<String>, text: String) -> Unit,
    onDelete: (Message) -> Unit,
    onMerge: (absorb: String) -> Unit,
) {
    val contacts = state.data.contacts
    val messages = thread?.messages ?: emptyList()
    val title = remember(identities, group, contacts) {
        buildList {
            addAll(identities.map { id -> contacts.firstOrNull { it.address == id }?.display ?: id })
            if (group) add("My group")
        }.joinToString(", ")
    }
    var text by rememberSaveable { mutableStateOf("") }
    var explain by remember { mutableStateOf<String?>(null) }
    var actions by remember { mutableStateOf<Message?>(null) }
    var info by remember { mutableStateOf<Message?>(null) }
    var toDelete by remember { mutableStateOf<Message?>(null) }
    var menu by remember { mutableStateOf(false) }
    var merging by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Send address per identity: default to the channel last used in this thread; user may switch.
    val single = identities.singleOrNull()?.let { id -> contacts.firstOrNull { it.address == id } }?.takeIf { it.aliases.isNotEmpty() }
    var via by rememberSaveable(identities, thread?.messages?.size) {
        mutableStateOf(single?.let { c -> preferred.firstOrNull(c::owns) ?: thread?.lastAddressFor(c.address, contacts) ?: c.address })
    }
    val sendTo = remember(identities, via, contacts, messages.size) {
        identities.map { id -> if (single != null && id == single.address) via ?: id else thread?.lastAddressFor(id, contacts) ?: id }
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    val est = remember(text, sendTo, group) { Payload.estimate(sendTo, text, group) }
    val canMerge = !group && identities.size == 1 && otherThreads.isNotEmpty()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                colors = ybTopBarColors(),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    if (canMerge) {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "More") }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Merge with another conversation…") }, onClick = { menu = false; merging = true })
                        }
                    }
                    ConnectPill(state, bluetoothOn, onConnect = onConnect, onDisconnect = onDisconnect, onOpenSheet = onStatus)
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    Bubble(m, onExplain = { explain = it }, onLongPress = { actions = m })
                }
            }
            Composer(
                text = text,
                onText = { text = it },
                est = est,
                via = single?.let { c -> via?.let { v -> c to v } },
                onVia = { v -> via = v },
                canQueue = text.isNotBlank() && (sendTo.isNotEmpty() || group),
                onQueue = { onQueue(sendTo, text.trim()); text = "" },
            )
        }
    }

    explain?.let {
        AlertDialog(
            onDismissRequest = { explain = null },
            confirmButton = { TextButton(onClick = { explain = null }) { Text("OK") } },
            text = { Text(it) },
        )
    }

    actions?.let { m ->
        val clipboard = LocalClipboardManager.current
        ModalBottomSheet(onDismissRequest = { actions = null }) {
            Text(
                m.body, Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            )
            ListItem(
                modifier = Modifier.clickable { info = m; actions = null },
                leadingContent = { Icon(Icons.Default.Info, null) }, headlineContent = { Text("Message info") },
            )
            ListItem(
                modifier = Modifier.clickable { clipboard.setText(AnnotatedString(m.body)); actions = null },
                leadingContent = { Text("⎘", style = MaterialTheme.typography.titleLarge) }, headlineContent = { Text("Copy text") },
            )
            ListItem(
                modifier = Modifier.clickable { toDelete = m; actions = null },
                leadingContent = { Icon(Icons.Default.Delete, null) },
                headlineContent = { Text(if (m.direction == Direction.OUT && m.outState == OutState.QUEUED) "Remove from queue" else "Delete from phone") },
            )
            Spacer(Modifier.padding(16.dp))
        }
    }

    info?.let { m -> MessageInfoDialog(m, contacts, onDismiss = { info = null }) }

    toDelete?.let { m ->
        val queued = m.direction == Direction.OUT && m.outState == OutState.QUEUED
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text(if (queued) "Remove from queue?" else "Delete from phone?") },
            text = {
                Text(
                    when {
                        queued -> "This message has not been sent to the YB yet and will not be."
                        m.direction == Direction.OUT -> "The YB already has this message; deleting here does not recall it."
                        else -> "Removes the message from this phone only."
                    },
                )
            },
            confirmButton = { TextButton(onClick = { onDelete(m); toDelete = null }) { Text(if (queued) "Remove" else "Delete") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancel") } },
        )
    }

    if (merging) {
        AlertDialog(
            onDismissRequest = { merging = false },
            title = { Text("Merge into this conversation") },
            text = {
                Column {
                    Text(
                        "Pick the conversation that belongs to the same person. Its address becomes another way to reach $title; replies from either address land here.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LazyColumn(Modifier.padding(top = 8.dp)) {
                        items(otherThreads, key = { it.key }) { t ->
                            val id = t.identities.single()
                            ListItem(
                                modifier = Modifier.clickable { onMerge(id); merging = false },
                                leadingContent = { Icon(channelIcon(id), channelName(id)) },
                                headlineContent = { Text(t.title(contacts)) },
                                supportingContent = if (contacts.any { it.address == id && it.name.isNotBlank() }) ({ Text(id) }) else null,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { merging = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Message, onExplain: (String) -> Unit, onLongPress: () -> Unit) {
    val out = m.direction == Direction.OUT
    val bg = if (out) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (out) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (out) Alignment.End else Alignment.Start) {
        Surface(
            color = bg, contentColor = fg,
            shape = RoundedCornerShape(16.dp, 16.dp, if (out) 4.dp else 16.dp, if (out) 16.dp else 4.dp),
            modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = onLongPress),
        ) {
            Text(m.body, Modifier.padding(horizontal = 12.dp, vertical = 8.dp), style = MaterialTheme.typography.bodyLarge)
        }
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            val muted = MaterialTheme.colorScheme.onSurfaceVariant
            m.counterparts.map(::channelIcon).distinct().forEach { Icon(it, channelName(m.counterparts.first()), Modifier.size(12.dp), tint = muted) }
            if (m.counterparts.isNotEmpty()) Spacer(Modifier.width(4.dp))
            Text(fmtWhen(m.at), style = MaterialTheme.typography.labelSmall, color = muted)
            if (out) {
                Spacer(Modifier.width(6.dp))
                val (glyph, why) = when (m.outState) {
                    OutState.QUEUED -> "⏳ Queued" to "Waiting on this phone for a connection to the YB. Long-press the message to remove it."
                    OutState.SENDING -> "↑ Sending ${m.acceptedParts}/${m.totalParts}" to
                        "Parts are being handed to the YB over Bluetooth; ${m.acceptedParts} of ${m.totalParts} accepted so far."
                    OutState.ACCEPTED -> "→ YB" to ACCEPTED_EXPLANATION
                }
                TextButton(onClick = { onExplain(why) }, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(glyph, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun MessageInfoDialog(m: Message, contacts: List<Contact>, onDismiss: () -> Unit) {
    val out = m.direction == Direction.OUT
    fun who(a: String): String {
        val name = contacts.firstOrNull { it.owns(a) }?.name?.ifBlank { null }
        return (if (name != null) "$name\n" else "") + "$a (${channelName(a)})"
    }
    val rows = buildList {
        if (out) {
            add("To" to (m.recipients.joinToString("\n", transform = ::who) + if (m.group) "\nMy group" else ""))
            add("Queued" to fmtFull(m.at))
            m.outParts.mapNotNull { it.sentAt }.maxOrNull()?.let { add("Sent to YB" to fmtFull(it)) }
            m.outParts.mapNotNull { it.acceptedAt }.maxOrNull()?.let {
                add("Accepted by YB" to "${fmtFull(it)} (${m.acceptedParts}/${m.totalParts} parts)")
            }
            add("Parts" to "${m.totalParts} · ybId ${m.outParts.joinToString(", ") { "0x%04X".format(it.ybId) }}")
            val est = Payload.estimate(m.recipients, m.text, m.group)
            add("Credits" to "~${est.credits}" + if (est.smsRecipients > 0) " (incl. ${est.smsRecipients} SMS)" else "")
        } else {
            add("From" to who(Payload.normaliseRecipient(m.from)))
            add("Received" to fmtFull(m.at))
            add("Parts" to "${m.inParts.size}/${m.totalParts}" + if (m.threadId != 0) " · device thread ${m.threadId}" else "")
            m.creditAfter?.let { add("Credit balance after" to "$it") }
        }
        add("Length" to "${m.body.length} chars")
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message info") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                rows.forEach { (k, v) ->
                    Row {
                        Text(k, Modifier.width(110.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun Composer(
    text: String,
    onText: (String) -> Unit,
    est: Payload.Estimate,
    via: Pair<Contact, String>?,
    onVia: (String) -> Unit,
    canQueue: Boolean,
    onQueue: () -> Unit,
) {
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            if (via != null) {
                val (c, current) = via
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Send via", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    c.addresses.forEach { a ->
                        AssistChip(
                            onClick = { onVia(a) },
                            label = { Text(channelName(a) + if (a == current) " ✓" else "", style = MaterialTheme.typography.labelSmall) },
                            leadingIcon = { Icon(channelIcon(a), null, Modifier.size(14.dp)) },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text, onValueChange = onText,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message") },
                    maxLines = 6,
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = onQueue, enabled = canQueue) { Text("Queue") }
            }
            Box(Modifier.padding(top = 4.dp)) {
                val sms = if (est.smsRecipients > 0) " (${est.smsRecipients} SMS)" else ""
                val grp = if (est.group) " + group" else ""
                Text(
                    "${est.chars} chars incl. address · ${est.parts} part${if (est.parts == 1) "" else "s"} · ~${est.credits} cr$sms$grp",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
