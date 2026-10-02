package io.github.jkinred.secondwind.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.jkinred.secondwind.Link
import io.github.jkinred.secondwind.Problem
import io.github.jkinred.secondwind.UiState

/**
 * Icon-only connection toggle for the top bar. Filled disc when connected (tap: disconnect),
 * spinner while connecting (tap: cancel), outline when idle (tap: connect). When something
 * blocks connecting — Bluetooth off, no device, no credentials — it shows a badge and tapping
 * opens the device sheet instead, where the problem is explained. Long-press always opens the sheet.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConnectPill(state: UiState, bluetoothOn: Boolean, onConnect: () -> Unit, onDisconnect: () -> Unit, onOpenSheet: () -> Unit) {
    val s = state.data.settings
    val blocked = state.link == Link.DISCONNECTED && (!bluetoothOn || s.deviceAddress.isEmpty() || !s.credentialsValid)
    val connected = state.link == Link.CONNECTED
    val onTap = when {
        connected || state.link == Link.CONNECTING -> onDisconnect
        blocked -> onOpenSheet
        else -> onConnect
    }
    val label = when {
        connected -> "Connected — tap to disconnect"
        state.link == Link.CONNECTING -> "Connecting — tap to cancel"
        blocked -> "Cannot connect — tap for details"
        else -> "Not connected — tap to connect"
    }
    Box(
        Modifier
            .padding(horizontal = 4.dp)
            .size(40.dp)
            .clip(CircleShape)
            .background(if (connected) OnYellow else Color.Transparent)
            .combinedClickable(onClick = onTap, onLongClick = onOpenSheet, role = Role.Button)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        val bg = if (isSystemInDarkTheme()) YellowbrickAmber else YellowbrickYellow
        Icon(YbIcon, null, Modifier.size(24.dp), tint = if (connected) bg else OnYellow.copy(alpha = if (state.link == Link.CONNECTING) 0.5f else 1f))
        if (state.link == Link.CONNECTING) CircularProgressIndicator(Modifier.size(34.dp), strokeWidth = 2.dp, color = OnYellow)
        if (blocked) Box(
            Modifier.align(Alignment.TopEnd).padding(2.dp).size(14.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error),
            contentAlignment = Alignment.Center,
        ) { Text("!", color = MaterialTheme.colorScheme.onError, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun SettingsPill(onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(Icons.Default.Settings, "Device and settings") }
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
