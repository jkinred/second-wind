package org.yb.secondwind.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.yb.secondwind.Link
import org.yb.secondwind.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeviceSheet(
    state: UiState,
    bluetoothOn: Boolean,
    onDismiss: () -> Unit,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onPull: () -> Unit,
    onSettings: () -> Unit,
    onTroubleshooting: () -> Unit,
    onGuide: () -> Unit,
) {
    val d = state.data
    val s = d.settings
    val connected = state.link == Link.CONNECTED
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(s.deviceName.ifEmpty { "No Yellowbrick chosen" }, style = MaterialTheme.typography.titleLarge)
                    Text(
                        when (state.link) {
                            Link.CONNECTED -> "Connected via ${d.lastConnectRung}"
                            Link.CONNECTING -> "Connecting…"
                            Link.DISCONNECTED -> if (!bluetoothOn) "Bluetooth off" else "Not connected"
                        },
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when (state.link) {
                    Link.CONNECTED -> FilledTonalButton(onClick = onDisconnect) { Text("Disconnect") }
                    Link.CONNECTING -> CircularProgressIndicator(Modifier.size(24.dp))
                    Link.DISCONNECTED -> Button(onClick = onConnect) { Text("Connect") }
                }
            }
            HorizontalDivider()

            InfoRow(
                "Credits",
                d.credit?.let { "~$it" } ?: "unknown",
                if (d.creditAt > 0) "as of ${fmtAge(d.creditAt)} · updates only when a message arrives" else "updates when a message arrives",
            )
            InfoRow(
                "Phone pull",
                if (d.lastPullAt > 0) fmtAge(d.lastPullAt) else "never",
                if (connected) "every 30 s while connected" else "asks the YB for queued messages when connected",
            )
            InfoRow(
                "Checks satellite",
                d.device?.inboxCheckInterval?.let { "every $it" } ?: "unknown",
                "device setting; whether it is enabled is not reported",
            )
            InfoRow("Tracking", d.device?.trackingInterval?.let { "every $it" } ?: "unknown", "device setting")
            InfoRow(
                "Storage",
                when (d.device?.storageNearlyFull) { true -> "⚠ nearly full"; false -> "OK"; null -> "unknown" },
                if (d.device?.storageNearlyFull == true) "≥ 490 of 500 message records on the YB; new messages may be dropped silently" else null,
            )
            InfoRow(
                "Last connected",
                if (d.lastConnectedAt > 0) fmtAge(d.lastConnectedAt) else "never",
                d.lastConnectRung.takeIf { it.isNotEmpty() }?.let { "via $it" },
            )
            InfoRow("Firmware", d.device?.firmware?.ifEmpty { null } ?: "unknown", d.device?.let { "read ${fmtAge(it.at)}" })
            InfoRow("Mode", s.mode.name.lowercase().replace('_', '-'), null)
            HorizontalDivider()

            OutlinedButton(onClick = onPull, enabled = connected && !state.pulling, modifier = Modifier.fillMaxWidth()) {
                if (state.pulling) { CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (s.mode.name == "HOTSPOT") "Check satellite mailbox now (costs credits)" else "Pull messages now")
            }
            OutlinedButton(onClick = onGuide, modifier = Modifier.fillMaxWidth()) { Text("Device guide") }
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = onSettings) { Text("Settings ›") }
                TextButton(onClick = onTroubleshooting) { Text("Troubleshooting ›") }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String, note: String?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Text(label, Modifier.width(120.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            if (note != null) Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
