package org.yb.secondwind.ui

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.yb.secondwind.Messenger
import org.yb.secondwind.bt.FoundDevice
import org.yb.secondwind.data.Contact
import org.yb.secondwind.data.Settings
import org.yb.secondwind.proto.Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: Messenger, settings: Settings, contacts: List<Contact>, granted: Boolean, onBack: () -> Unit, onTroubleshooting: () -> Unit, onAbout: () -> Unit) {
    var keyword by remember { mutableStateOf(settings.keyword) }
    var password by remember { mutableStateOf(settings.password) }
    var mode by remember { mutableStateOf(settings.mode) }
    var device by remember { mutableStateOf(settings.deviceAddress to settings.deviceName) }
    var scanning by remember { mutableStateOf(false) }
    val found = remember { mutableStateListOf<FoundDevice>() }
    var renaming by remember { mutableStateOf<Contact?>(null) }

    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (vm.bluetooth.isEnabled) scanning = true
    }
    LaunchedEffect(scanning) {
        if (!scanning) return@LaunchedEffect
        found.clear()
        try { vm.bluetooth.scan().collect { d -> if (found.none { it.address == d.address }) found += d } } finally { scanning = false }
    }

    fun save() = vm.saveSettings(
        settings.copy(keyword = keyword.trim(), password = password.trim(), mode = mode, deviceAddress = device.first, deviceName = device.second),
    )
    BackHandler { save(); onBack() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = { save(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Yellowbrick", style = MaterialTheme.typography.titleMedium)
            Text(if (device.first.isEmpty()) "None chosen" else "${device.second} (${device.first})", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = { if (vm.bluetooth.isEnabled) scanning = true else enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
                    enabled = granted && !scanning,
                ) { Text(if (scanning) "Scanning…" else "Scan for Yellowbricks") }
                if (scanning) CircularProgressIndicator(Modifier.size(20.dp))
            }
            if (!granted) Text("Bluetooth permission not granted.", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            found.forEach { d ->
                ListItem(
                    modifier = Modifier.clickable { device = d.address to d.name; scanning = false },
                    headlineContent = { Text(d.name) },
                    supportingContent = { Text(d.address + if (d.bonded) " · paired" else "") },
                    trailingContent = { if (d.address == device.first) Text("✓") },
                )
            }
            HorizontalDivider()

            Text("Account", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = keyword, onValueChange = { if (it.length <= 4) keyword = it }, label = { Text("Keyword") }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = password, onValueChange = { if (it.length <= 4) password = it }, label = { Text("Password") }, singleLine = true, modifier = Modifier.weight(1f))
            }
            Text("Both are 4 characters, issued by YB Tracking for this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Mode.entries.forEach { m ->
                    FilterChip(selected = mode == m, onClick = { mode = m }, label = { Text(m.name.lowercase().replace('_', '-')) })
                }
            }
            Text(
                if (mode == Mode.STAND_ALONE) "Stand-alone: the YB checks the satellite mailbox on its own schedule."
                else "Hotspot: this phone triggers satellite mailbox checks; each check costs credits.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            HorizontalDivider()

            Text("Favourites", style = MaterialTheme.typography.titleMedium)
            if (contacts.isEmpty()) Text("None yet. Star a recent recipient or pick from phone contacts when writing a message.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            contacts.forEach { c ->
                ListItem(
                    modifier = Modifier.clickable { renaming = c },
                    headlineContent = { Text(c.display) },
                    supportingContent = if (c.name.isNotBlank()) ({ Text(c.address) }) else null,
                    trailingContent = { TextButton(onClick = { vm.unpinContact(c.address) }) { Text("Remove") } },
                )
            }
            HorizontalDivider()

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { save(); onTroubleshooting() }) { Text("Troubleshooting ›") }
                TextButton(onClick = { save(); onAbout() }) { Text("About ›") }
            }
        }
    }

    renaming?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("Rename favourite") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true) },
            confirmButton = { TextButton(onClick = { vm.pinContact(c.address, name.trim()); renaming = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TroubleshootingScreen(vm: Messenger, log: List<String>, deviceName: String, onBack: () -> Unit) {
    val clipboard = LocalClipboardManager.current
    var confirmForget by remember { mutableStateOf(false) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Troubleshooting") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(onClick = { clipboard.setText(AnnotatedString(vm.exportLog())) }) { Text("Copy log") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "The Yellowbrick keeps one phone's pairing. If another phone has paired with it, this phone is rejected until it pairs again. The app does this automatically when it detects the condition; use the button if it did not.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = { confirmForget = true }, enabled = deviceName.isNotEmpty()) { Text("Forget pairing and reconnect") }
            }
            HorizontalDivider()
            Text("Log", Modifier.padding(start = 16.dp, top = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            LazyColumn(Modifier.weight(1f)) {
                items(log) { Text(it, Modifier.padding(horizontal = 16.dp, vertical = 1.dp), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace) }
            }
        }
    }
    if (confirmForget) {
        AlertDialog(
            onDismissRequest = { confirmForget = false },
            title = { Text("Forget pairing?") },
            text = { Text("Android will forget $deviceName. The next connection shows a pairing prompt; keep the Yellowbrick close and accept it.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmForget = false
                    if (vm.forgetBond()) vm.connect(hasPermission = true)
                }) { Text("Forget and reconnect") }
            },
            dismissButton = { TextButton(onClick = { confirmForget = false }) { Text("Cancel") } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(version: String, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("About") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).padding(16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("YB Second Wind $version", style = MaterialTheme.typography.titleLarge)
            Text("Android replacement for the discontinued YB Messenger app, for Yellowbrick v3 / MkII satellite trackers.")
            Text(
                "Not affiliated with, endorsed by or supported by YB Tracking Ltd. \"Yellowbrick\" and \"YB\" are their marks, used here only to identify the device this app talks to. Use of a third-party app with your airtime account is between you and your airtime provider.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                "Message states: Queued = waiting on this phone. Sending = being handed to the YB over Bluetooth. → YB = $ACCEPTED_EXPLANATION The YB never reports transmission or delivery, so → YB is the final state this app can show.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Licensed under the Apache License 2.0. Source and protocol documentation are published with the app.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(1.dp))
        }
    }
}
