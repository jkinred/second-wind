package io.github.jkinred.secondwind.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.jkinred.secondwind.Link
import io.github.jkinred.secondwind.UiState
import io.github.jkinred.secondwind.data.Contact
import io.github.jkinred.secondwind.data.Direction
import io.github.jkinred.secondwind.data.OutState
import io.github.jkinred.secondwind.data.Thread

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: UiState,
    threads: List<Thread>,
    bluetoothOn: Boolean,
    problem: ProblemCopy?,
    queued: Int,
    onDismissProblem: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onStatus: () -> Unit,
    onOpen: (Thread) -> Unit,
    onNew: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Second Wind") },
                colors = ybTopBarColors(),
                actions = {
                    SettingsPill(onStatus)
                    ConnectPill(state, bluetoothOn, onConnect = onConnect, onDisconnect = onDisconnect, onOpenSheet = onStatus)
                    Spacer(Modifier.width(8.dp))
                },
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onNew) { Icon(Icons.Default.Edit, "New message") } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            when {
                problem != null -> Banner(problem.text, problem.action, problem.run, onDismissProblem)
                queued > 0 && state.link == Link.DISCONNECTED ->
                    Banner("$queued message${if (queued == 1) "" else "s"} waiting to send.", "Connect", onConnect, null, error = false)
            }
            if (threads.isEmpty()) {
                Column(Modifier.padding(24.dp)) {
                    Text("No conversations yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (state.data.settings.deviceAddress.isEmpty()) "Tap ⚙ to choose your Yellowbrick and enter your keyword and password."
                        else "Tap ✎ to write your first message.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn {
                items(threads, key = { it.key }) { t ->
                    ThreadRow(t, state.data.contacts, onClick = { onOpen(t) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ThreadRow(t: Thread, contacts: List<Contact>, onClick: () -> Unit) {
    val m = t.latest
    val unread = t.unread > 0
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(
                Modifier.size(10.dp).background(
                    if (unread) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface, CircleShape,
                ),
            )
        },
        headlineContent = { Text(t.title(contacts), fontWeight = if (unread) FontWeight.Bold else FontWeight.Normal, maxLines = 1) },
        supportingContent = {
            val prefix = if (m.direction == Direction.OUT) "You: " else ""
            Text(prefix + m.body.take(140), maxLines = 2, fontWeight = if (unread) FontWeight.Medium else FontWeight.Normal)
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(fmtWhen(m.at), style = MaterialTheme.typography.labelSmall)
                when {
                    t.queued > 0 && t.messages.any { it.outState == OutState.SENDING } -> Text("↑ sending", style = MaterialTheme.typography.labelSmall)
                    t.queued > 0 -> Text("⏳ ${t.queued} queued", style = MaterialTheme.typography.labelSmall)
                }
            }
        },
    )
}
