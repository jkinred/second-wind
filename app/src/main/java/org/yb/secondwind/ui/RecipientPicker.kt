package org.yb.secondwind.ui

import android.content.Intent
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.yb.secondwind.data.Contact
import org.yb.secondwind.proto.Payload

/**
 * New message: build a recipient set from favourites, recents, phone contacts or typed text.
 * Phone contacts come via the system picker on a single e-mail/phone row, which grants read
 * access to that row only — no contacts permission.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipientPicker(
    favourites: List<Contact>,
    recents: List<Contact>,
    onBack: () -> Unit,
    onPin: (address: String, name: String) -> Unit,
    onUnpin: (address: String) -> Unit,
    onNext: (addresses: List<String>, group: Boolean) -> Unit,
) {
    val chosen = remember { mutableStateListOf<String>() }
    var group by remember { mutableStateOf(false) }
    var typed by remember { mutableStateOf("") }
    var naming by remember { mutableStateOf<Contact?>(null) }
    val ctx = LocalContext.current

    fun add(address: String) {
        val a = Payload.normaliseRecipient(address)
        if (a.isNotEmpty() && a !in chosen) chosen += a
    }
    fun commitTyped() {
        typed.split(',', ';', ' ').forEach(::add)
        typed = ""
    }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.Data.DATA1, ContactsContract.Data.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val address = c.getString(0) ?: return@use
                val name = c.getString(1) ?: ""
                add(address)
                onPin(address, name)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(
                        enabled = chosen.isNotEmpty() || group || typed.isNotBlank(),
                        onClick = { commitTyped(); onNext(chosen.toList(), group) },
                    ) { Text("Next") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (group) InputChip(selected = true, onClick = { group = false }, label = { Text("My group") }, trailingIcon = { Text("×") })
                    chosen.forEach { a ->
                        val name = favourites.firstOrNull { it.owns(a) }?.name?.ifBlank { null }
                        InputChip(selected = true, onClick = { chosen -= a }, label = { Text(name ?: a) }, trailingIcon = { Text("×") })
                    }
                }
                OutlinedTextField(
                    value = typed, onValueChange = { typed = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("To: e-mail or phone, or pick below") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitTyped() }),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI)) }) { Text("E-mail from contacts") }
                    OutlinedButton(onClick = { pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)) }) { Text("Phone from contacts") }
                }
            }
            LazyColumn {
                item { SectionHeader("Favourites") }
                item {
                    ListItem(
                        modifier = Modifier.clickable { group = !group },
                        headlineContent = { Text("My group") },
                        supportingContent = { Text("Your airtime account's group list") },
                        trailingContent = { if (group) Text("✓") },
                    )
                }
                items(favourites, key = { "f" + it.address }) { c ->
                    val picked = c.addresses.firstOrNull { it in chosen }
                    ContactRow(c, selected = picked != null, onClick = { if (picked != null) chosen -= picked else add(c.address) },
                        star = true, onStar = { onUnpin(c.address) })
                    // A merged contact can be reached two ways; offer the alternates explicitly.
                    if (c.aliases.isNotEmpty()) Row(Modifier.padding(start = 56.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.addresses.forEach { a ->
                            InputChip(selected = a in chosen, onClick = { if (a in chosen) chosen -= a else { c.addresses.forEach { chosen -= it }; add(a) } },
                                label = { Text(channelName(a), style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(channelIcon(a), null, Modifier.width(14.dp)) })
                        }
                    }
                }
                if (recents.isNotEmpty()) item { SectionHeader("Recent") }
                items(recents, key = { "r" + it.address }) { c ->
                    ContactRow(c, selected = c.address in chosen, onClick = { if (c.address in chosen) chosen -= c.address else add(c.address) },
                        star = false, onStar = { naming = c })
                }
                item { Spacer(Modifier.padding(24.dp)) }
            }
        }
    }

    naming?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("Add to favourites") },
            text = {
                Column {
                    Text(c.address, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name (optional)") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { onPin(c.address, name.trim()); naming = null }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) =
    Text(text.uppercase(), Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

@Composable
private fun ContactRow(c: Contact, selected: Boolean, onClick: () -> Unit, star: Boolean, onStar: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        headlineContent = { Text(c.display) },
        supportingContent = if (c.name.isNotBlank() || c.aliases.isNotEmpty()) ({ Text(c.addresses.joinToString(" · ")) }) else null,
        leadingContent = { Text(if (selected) "✓" else " ", Modifier.width(16.dp)) },
        trailingContent = {
            IconButton(onClick = onStar) {
                Icon(if (star) Icons.Filled.Star else Icons.Outlined.Star, if (star) "Remove from favourites" else "Add to favourites")
            }
        },
    )
}
