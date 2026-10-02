package io.github.jkinred.secondwind.ui

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
import io.github.jkinred.secondwind.Link
import io.github.jkinred.secondwind.Messenger
import io.github.jkinred.secondwind.data.Direction
import io.github.jkinred.secondwind.data.OutState
import io.github.jkinred.secondwind.data.Threads
import io.github.jkinred.secondwind.proto.Payload

private sealed interface Screen {
    data object Home : Screen
    class Thread(val identities: List<String>, val group: Boolean, val preferred: List<String> = emptyList()) : Screen
    data object New : Screen
    data object Settings : Screen
    data object Troubleshooting : Screen
    data object About : Screen
    data object Manual : Screen
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
    val contacts = state.data.contacts
    val threads = remember(state.data.messages, contacts) { Threads.build(state.data.messages, contacts) }
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
            onDismissProblem = vm::dismissProblem, onConnect = connect, onDisconnect = vm::disconnect, onStatus = { sheet = true },
            onOpen = { t -> vm.markRead(t.key); screen = Screen.Thread(t.identities, t.group) },
            onNew = { screen = Screen.New },
        )
        is Screen.Thread -> {
            val key = Threads.key(s.identities, s.group, contacts)
            val thread = remember(threads, key) { threads.firstOrNull { it.key == key } }
            val others = remember(threads, key) { threads.filter { it.key != key && !it.group && it.identities.size == 1 } }
            LaunchedEffect(thread?.messages?.size) { vm.markRead(key) }
            ThreadScreen(
                state = state, identities = s.identities, preferred = s.preferred, group = s.group, thread = thread, otherThreads = others,
                bluetoothOn = bluetoothOn,
                onBack = { screen = Screen.Home }, onStatus = { sheet = true }, onConnect = connect, onDisconnect = vm::disconnect,
                onQueue = { addrs, text -> vm.queue(addrs, text, s.group); if (state.link == Link.DISCONNECTED && state.problem == null) connect() },
                onDelete = { vm.delete(it.id) },
                onMerge = { absorb -> vm.mergeContacts(keep = s.identities.single(), absorb = absorb) },
            )
        }
        Screen.New -> RecipientPicker(
            favourites = contacts.filter { it.pinned }.sortedBy { it.display.lowercase() },
            recents = remember(state.data.messages, contacts) { Threads.recents(state.data.messages, contacts) },
            onBack = { screen = Screen.Home },
            onPin = vm::pinContact, onUnpin = vm::unpinContact,
            onNext = { addrs, group ->
                screen = Screen.Thread(addrs.map { Threads.resolve(it, contacts) }.distinct(), group, preferred = addrs.map(Payload::normaliseRecipient))
            },
        )
        Screen.Settings -> SettingsScreen(
            vm, state.data.settings, contacts.sortedBy { it.display.lowercase() }, granted,
            onBack = { screen = Screen.Home }, onTroubleshooting = { screen = Screen.Troubleshooting }, onAbout = { screen = Screen.About },
        )
        Screen.Troubleshooting -> TroubleshootingScreen(vm, state.log, state.data.settings.deviceName, onBack = { screen = Screen.Settings })
        Screen.About -> AboutScreen(version, onBack = { screen = Screen.Settings })
        Screen.Manual -> ManualScreen(onBack = { screen = Screen.Home })
    }

    if (sheet) DeviceSheet(
        state = state, bluetoothOn = bluetoothOn,
        onDismiss = { sheet = false },
        onConnect = { sheet = false; connect() },
        onDisconnect = vm::disconnect,
        onPull = vm::pullNow,
        onSettings = { sheet = false; screen = Screen.Settings },
        onTroubleshooting = { sheet = false; screen = Screen.Troubleshooting },
        onGuide = { sheet = false; screen = Screen.Manual },
    )
}
