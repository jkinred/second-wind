package org.yb.secondwind.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.yb.secondwind.Link
import org.yb.secondwind.Problem
import org.yb.secondwind.UiState

/** The device's casing colour; used only for the connected dot so it reads against any Material scheme. */
val YellowbrickYellow = Color(0xFFFFD500)

/** `● Connected · 46 cr` / `◌ Connecting…` / `○ Not connected` / `⚠ Bluetooth off` / `○ No device`. */
@Composable
fun StatusChip(state: UiState, bluetoothOn: Boolean, onClick: () -> Unit) {
    val d = state.data
    val (dot, label, colour) = when {
        state.link == Link.CONNECTED -> Triple("●", "Connected" + (d.credit?.let { " · ~$it cr" } ?: ""), YellowbrickYellow)
        state.link == Link.CONNECTING -> Triple("◌", "Connecting…", MaterialTheme.colorScheme.onSurfaceVariant)
        !bluetoothOn -> Triple("⚠", "Bluetooth off", MaterialTheme.colorScheme.error)
        d.settings.deviceAddress.isEmpty() -> Triple("○", "No device", MaterialTheme.colorScheme.onSurfaceVariant)
        else -> Triple("○", "Not connected", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    AssistChip(
        onClick = onClick,
        label = { Text(label) },
        leadingIcon = {
            if (state.link == Link.CONNECTING) CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
            else Text(dot, color = colour, fontWeight = FontWeight.Bold)
        },
    )
}

class ProblemCopy(val text: String, val action: String?, val run: () -> Unit)

/** Human copy + remedy for each [Problem]; raw detail stays in the log. */
fun problemCopy(
    p: Problem,
    deviceName: String,
    queued: Int,
    onGrant: () -> Unit,
    onEnableBluetooth: () -> Unit,
    onSettings: () -> Unit,
    onBluetoothSettings: () -> Unit,
    onRetry: () -> Unit,
): ProblemCopy {
    val name = deviceName.ifEmpty { "the Yellowbrick" }
    val waiting = if (queued > 0) " $queued message${if (queued == 1) "" else "s"} waiting to send." else ""
    return when (p) {
        Problem.PermissionNeeded -> ProblemCopy("Bluetooth permission is needed to talk to $name.", "Grant", onGrant)
        Problem.BluetoothOff -> ProblemCopy("Bluetooth is off.$waiting", "Turn on", onEnableBluetooth)
        Problem.NoDevice -> ProblemCopy("No Yellowbrick chosen yet.", "Choose device", onSettings)
        Problem.CredentialsMissing -> ProblemCopy("Keyword and password must each be 4 characters.", "Open settings", onSettings)
        Problem.CredentialsRejected -> ProblemCopy("$name rejected your keyword or password.", "Open settings", onSettings)
        is Problem.Unreachable -> ProblemCopy("Can't reach $name. Is it on and within a few metres?$waiting", "Retry", onRetry)
        is Problem.StaleBond -> ProblemCopy(
            "$name no longer accepts this phone's pairing — usually because another phone paired with it. Forget it in Android's Bluetooth settings, then retry.",
            "Bluetooth settings", onBluetoothSettings,
        )
        is Problem.RepairFailed -> ProblemCopy("Re-pairing didn't complete. Keep $name close, retry, and accept the pairing prompt.", "Retry", onRetry)
        is Problem.Dropped -> ProblemCopy("Connection to $name dropped.$waiting", "Reconnect", onRetry)
    }
}

@Composable
fun Banner(text: String, action: String?, onAction: () -> Unit, onDismiss: (() -> Unit)?, error: Boolean = true) {
    val bg = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val fg = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(color = bg, contentColor = fg, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            if (action != null) {
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = onAction) { Text(action, color = fg) }
            }
            if (onDismiss != null) IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Dismiss", tint = fg) }
        }
    }
}
