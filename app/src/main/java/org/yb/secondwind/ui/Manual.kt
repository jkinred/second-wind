package org.yb.secondwind.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

private const val MANUAL_URL = "https://cdn.yb.tl/yb_mk2_manual.pdf"

/**
 * Device quick reference for the Yellowbrick v3 MkII, written for use alongside this app.
 * Facts are drawn from the manufacturer's user guide v2.0.0 (2013) and restated in our own words;
 * the guide itself is copyright Rock Seven and is linked rather than reproduced.
 */
private class Section(val title: String, val body: List<String>, val table: List<List<String>> = emptyList())

private val SECTIONS = listOf(
    Section(
        "Buttons and lights",
        listOf(
            "Five navigation buttons and one alert button under the flap at the bottom. OK opens the menu when the device is active; UP/DOWN scroll.",
            "Hold OK 5 s to activate. Choose DEACTIVATE from the menu to put it into standby — it cannot be fully switched off.",
            "Hold UP 2 s from the home screen to send a manual position report now.",
            "Red LED steady: charging. Red flashing for 5 min: charge complete. Charging from empty takes 6–8 h over USB; the device works while charging.",
            "Home screen shows In/Out message counts, battery %, and what the tracker is doing. UP/DOWN shows the last report's GPS and Iridium result.",
        ),
    ),
    Section(
        "Alert button",
        listOf(
            "Lift the flap and hold the alert button 5 s. The screen shows 'Sending Alert'. Works even when the device is deactivated.",
            "Give it sky: alerts and all other traffic need a clear view upwards. Inside vehicles, buildings or dense canopy, move to a clearing, dashboard, rear window or windowsill.",
            "Where the alert goes and who is notified is configured on the YB web account, not on the device.",
        ),
    ),
    Section(
        "Bluetooth and this app",
        listOf(
            "Menu → Bluetooth → Turn on. Bluetooth is off by default to save battery and must be on for this app to connect.",
            "Bluetooth messaging ('Advanced Messaging') is not available on the Basic model.",
            "The device keeps one phone's pairing at a time. Pairing from a second phone makes the first one fail until it re-pairs — this app detects that and re-pairs automatically, or use Troubleshooting.",
            "Messages written here are handed to the device over Bluetooth; the device sends them to the satellite when it next has sky, immediately if 'send now' is in effect or with the next scheduled report otherwise.",
        ),
    ),
    Section(
        "Messages and credits",
        listOf(
            "Each device has its own e-mail address ending in @my.yb.tl. Replies to anything you send — e-mail or SMS — are routed back to the device and appear in this app.",
            "Only senders on your 'allowed' list (set on the YB web account) can e-mail the device unprompted.",
            "Billing rule used by this app's estimate: 1 credit per 50 payload characters (address included), plus 1 credit per SMS recipient. The device's own balance arrives only with incoming messages, so the chip shows '~'.",
            "Inbox Checking (Messages menu) makes the device poll for mail every N minutes and costs credits like a position report. A normal tracking report already checks the mailbox, so do not run both — it doubles the spend.",
            "From the device's Outbox you can force an immediate retry of everything waiting; from Inbox and Sent you can delete or (Inbox) reply with a preset.",
            "'Remove All' clears the device's Inbox and Sent lists. Messages stored on this phone are unaffected.",
        ),
    ),
    Section(
        "Tracking",
        listOf(
            "Tracking → Status on/off toggles automatic position reports. Report Frequency ranges from Continuous to every 12 h; the app shows the configured interval on the device sheet.",
            "Burst mode collects fixes often (e.g. every 5 s) and sends them batched (e.g. once a minute) to save airtime. GPS Hot keeps GPS on so manual reports fix instantly. Both drain the battery faster.",
            "GPS Wakeup (Settings) is how early the GPS starts before a scheduled report: 20 s by default, up to 3 min for poor sky.",
            "History shows the last 10 attempted reports with time, position, altitude and whether Iridium accepted them.",
        ),
    ),
    Section(
        "Battery life (manufacturer figures)",
        listOf("Assumes display off, Bluetooth off, 20 s GPS wakeup, clear sky. A poor view of the sky can cut battery life by 10×; Bluetooth, GPS Hot, Burst and a lit screen all shorten it."),
        table = listOf(
            listOf("Interval", "Reports", "Days"),
            listOf("Continuous", "6000", "3.3"),
            listOf("5 min", "3300", "11.5"),
            listOf("15 min", "2900", "30"),
            listOf("30 min", "2500", "52"),
            listOf("1 h", "2200", "92"),
            listOf("4 h", "1500", "250"),
            listOf("8 h", "1050", "350"),
        ),
    ),
    Section(
        "If it misbehaves",
        listOf(
            "Deactivate, wait for the green LED to stop flashing, activate again.",
            "Hard reset: hold DOWN + ALERT for 10 s until the screen blanks and the red LED lights, then release.",
            "Screen lock PIN defaults to 1234 (Settings → Screen lock).",
            "Charge fully before assuming a fault. Clean with a damp cloth only; a little silicone grease on the USB cap seal keeps water out.",
            "Firmware upgrade instructions are a separate manufacturer document; this app only reads the version.",
        ),
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    var open by rememberSaveable { mutableStateOf(0) }
    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Device guide") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = { TextButton(onClick = { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(MANUAL_URL))) }) { Text("Full manual (PDF)") } },
        )
    }) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            Text(
                "Yellowbrick v3 MkII, condensed for use with this app. Based on the manufacturer's user guide v2.0.0; the guide is the authority where they differ.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SECTIONS.forEachIndexed { i, s ->
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth().clickable { open = if (open == i) -1 else i }.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(s.title, style = MaterialTheme.typography.titleMedium)
                    Text(if (open == i) "▾" else "▸", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AnimatedVisibility(open == i) {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        s.body.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                        if (s.table.isNotEmpty()) Column(Modifier.padding(top = 4.dp)) {
                            s.table.forEachIndexed { r, row ->
                                Row {
                                    row.forEachIndexed { c, cell ->
                                        Text(
                                            cell, Modifier.width(if (c == 0) 110.dp else 80.dp),
                                            style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                            color = if (r == 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            HorizontalDivider()
            Text(
                "Source: Rock Seven Mobile Services, 'User Guide for Yellowbrick V3 MkII' v2.0.0 (2013). Yellowbrick is a trademark of its owner; this app is not affiliated.",
                Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
