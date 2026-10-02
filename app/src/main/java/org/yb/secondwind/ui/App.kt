package org.yb.secondwind.ui

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.yb.secondwind.Link
import org.yb.secondwind.Messenger
import org.yb.secondwind.data.Direction
import org.yb.secondwind.data.OutState
import org.yb.secondwind.data.Threads

private sealed interface Screen {
    data object Home : Screen
    class Thread(val addresses: List<String>, val group: Boolean) : Screen
    data object New : Screen
    data object Settings : Screen
    data object Troubleshooting : Screen
    data object About : Screen
}

private val BT_PERMS = arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)

@Composable
fun App(vm: Messenger, version: String) {
    val ctx = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    var sheet by remember { mutableStateOf(false) }
    var granted by remember { mutableStateOf(BT_PERMS.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED }) }
    var bluetoothOn by remember { mutableStateOf(vm.bluetooth.isEnabled) }

    val requestPerms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        granted = r.values.all { it }
        if (granted) { vm.dismissProblem(); vm.connect(hasPermission = true) }
    }
    val enableBt = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        bluetoothOn = vm.bluetooth.isEnabled
        if (bluetoothOn) { vm.dismissProblem(); vm.connect(hasPermission = granted) }
    }

    // Track adapter state so the chip/banner follow the user toggling Bluetooth outside the app.
    DisposableEffect(Unit) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context, i: Intent) { bluetoothOn = vm.bluetooth.isEnabled }
        }
        ctx.registerReceiver(receiver, android.content.IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { ctx.unregisterReceiver(receiver) }
    }

    // One automatic connect per process start when everything needed is present.
    LaunchedEffect(Unit) {
        val s = state.data.settings
        if (granted && bluetoothOn && s.deviceAddress.isNotEmpty() && s.credentialsValid) vm.connect(hasPermission = true)
    }

    val connect = { if (granted) vm.connect(hasPermission = true) else requestPerms.launch(BT_PERMS) }
    val threads = remember(state.data.messages) { Threads.build(state.data.messages) }
    val queued = state.data.messages.count { it.direction == Direction.OUT && it.outState != OutState.ACCEPTED }
    val problem = state.problem?.let {
        problemCopy(
            it, state.data.settings.deviceName, queued,
            onGrant = { requestPerms.launch(BT_PERMS) },
            onEnableBluetooth = { enableBt.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) },
            onSettings = { vm.dismissProblem(); screen = Screen.Settings },
            onBluetoothSettings = { ctx.startActivity(Intent(AndroidSettings.ACTION_BLUETOOTH_SETTINGS)) },
            onRetry = { vm.dismissProblem(); connect() },
        )
    }

    BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }

    when (val s = screen) {
        Screen.Home -> HomeScreen(
            state = state, threads = threads, bluetoothOn = bluetoothOn, problem = problem, queued = queued,
            onDismissProblem = vm::dismissProblem, onConnect = connect, onStatus = { sheet = true },
            onOpen = { t -> vm.markRead(t.key); screen = Screen.Thread(t.addresses, t.group) },
            onNew = { screen = Screen.New },
        )
        is Screen.Thread -> {
            val key = Threads.key(s.addresses, s.group)
            val msgs = remember(threads, key) { threads.firstOrNull { it.key == key }?.messages ?: emptyList() }
            LaunchedEffect(msgs.size) { vm.markRead(key) }
            ThreadScreen(
                state = state, addresses = s.addresses, group = s.group, messages = msgs, bluetoothOn = bluetoothOn,
                onBack = { screen = Screen.Home }, onStatus = { sheet = true },
                onQueue = { text -> vm.queue(s.addresses, text, s.group); if (state.link == Link.DISCONNECTED && state.problem == null) connect() },
                onDelete = { vm.delete(it.id) },
            )
        }
        Screen.New -> RecipientPicker(
            favourites = state.data.contacts.sortedBy { it.display.lowercase() },
            recents = remember(state.data.messages, state.data.contacts) { Threads.recents(state.data.messages, state.data.contacts) },
            onBack = { screen = Screen.Home },
            onPin = vm::pinContact, onUnpin = vm::unpinContact,
            onNext = { addrs, group -> screen = Screen.Thread(addrs, group) },
        )
        Screen.Settings -> SettingsScreen(
            vm, state.data.settings, state.data.contacts.sortedBy { it.display.lowercase() }, granted,
            onBack = { screen = Screen.Home }, onTroubleshooting = { screen = Screen.Troubleshooting }, onAbout = { screen = Screen.About },
        )
        Screen.Troubleshooting -> TroubleshootingScreen(vm, state.log, state.data.settings.deviceName, onBack = { screen = Screen.Settings })
        Screen.About -> AboutScreen(version, onBack = { screen = Screen.Settings })
    }

    if (sheet) DeviceSheet(
        state = state, bluetoothOn = bluetoothOn,
        onDismiss = { sheet = false },
        onConnect = { sheet = false; connect() },
        onDisconnect = vm::disconnect,
        onPull = vm::pullNow,
        onSettings = { sheet = false; screen = Screen.Settings },
        onTroubleshooting = { sheet = false; screen = Screen.Troubleshooting },
    )
}
