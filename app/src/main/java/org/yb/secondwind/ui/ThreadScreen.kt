package org.yb.secondwind.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.yb.secondwind.UiState
import org.yb.secondwind.data.Contact
import org.yb.secondwind.data.Direction
import org.yb.secondwind.data.Message
import org.yb.secondwind.data.OutState
import org.yb.secondwind.proto.Payload

const val ACCEPTED_EXPLANATION =
    "The YB accepted every part of this message and queued for transmission, which occurs when connected to satellite."

/** A conversation with one address set. Works for a brand-new thread (no messages yet). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThreadScreen(
    state: UiState,
    addresses: List<String>,
    group: Boolean,
    messages: List<Message>,
    bluetoothOn: Boolean,
    onBack: () -> Unit,
    onStatus: () -> Unit,
    onQueue: (String) -> Unit,
    onDelete: (Message) -> Unit,
) {
    val contacts = state.data.contacts
    val title = remember(addresses, group, contacts) {
        buildList {
            addAll(addresses.map { a -> contacts.firstOrNull { it.address == a }?.display ?: a })
            if (group) add("My group")
        }.joinToString(", ")
    }
    var text by rememberSaveable { mutableStateOf("") }
    var explain by remember { mutableStateOf<String?>(null) }
    var toDelete by remember { mutableStateOf<Message?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }

    val est = remember(text, addresses, group) { Payload.estimate(addresses, text, group) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = { StatusChip(state, bluetoothOn, onStatus); Spacer(Modifier.width(8.dp)) },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(messages, key = { it.id }) { m ->
                    Bubble(
                        m,
                        contacts,
                        onExplain = { explain = it },
                        onLongPress = { toDelete = m },
                    )
                }
            }
            Composer(
                text = text,
                onText = { text = it },
                est = est,
                canQueue = text.isNotBlank() && (addresses.isNotEmpty() || group),
                onQueue = { onQueue(text.trim()); text = "" },
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
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Bubble(m: Message, contacts: List<Contact>, onExplain: (String) -> Unit, onLongPress: () -> Unit) {
    val out = m.direction == Direction.OUT
    val bg = if (out) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (out) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = if (out) Alignment.End else Alignment.Start) {
        Surface(
            color = bg, contentColor = fg,
            shape = RoundedCornerShape(16.dp, 16.dp, if (out) 4.dp else 16.dp, if (out) 16.dp else 4.dp),
            modifier = Modifier.widthIn(max = 320.dp).combinedClickable(onClick = {}, onLongClick = onLongPress),
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!out && m.from.isNotEmpty() && contacts.none { it.address == Payload.normaliseRecipient(m.from) }) {
                    Text(m.from, style = MaterialTheme.typography.labelSmall)
                }
                Text(m.body, style = MaterialTheme.typography.bodyLarge)
            }
        }
        Row(Modifier.padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(fmtWhen(m.at), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (out) {
                Spacer(Modifier.width(6.dp))
                val (glyph, why) = when (m.outState) {
                    OutState.QUEUED -> "⏳ Queued" to "Waiting on this phone for a connection to the YB. Long-press the message to remove it."
                    OutState.SENDING -> "↑ Sending ${m.acceptedParts}/${m.totalParts}" to
                        "Parts are being handed to the YB over Bluetooth; ${m.acceptedParts} of ${m.totalParts} accepted so far."
                    OutState.ACCEPTED -> "→ YB" to ACCEPTED_EXPLANATION
                }
                TextButton(onClick = { onExplain(why) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp)) {
                    Text(glyph, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun Composer(text: String, onText: (String) -> Unit, est: Payload.Estimate, canQueue: Boolean, onQueue: () -> Unit) {
    Surface(tonalElevation = 2.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
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
