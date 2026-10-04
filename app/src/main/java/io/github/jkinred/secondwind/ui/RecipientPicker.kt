package io.github.jkinred.secondwind.ui

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
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.jkinred.secondwind.data.Contact
import io.github.jkinred.secondwind.proto.Payload

private data class PendingRecipient(
    val address: String,
    val name: String = "",
    val phoneOnly: Boolean = '@' !in address,
    val pin: Boolean = false,
    val select: Boolean = true,
    val replace: Contact? = null,
)

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
    var group by rememberSaveable { mutableStateOf(false) }
    var typed by rememberSaveable { mutableStateOf("") }
    var inputError by rememberSaveable { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf<Contact?>(null) }
    var correcting by remember { mutableStateOf<PendingRecipient?>(null) }
    var pickingPhone by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current

    fun addPrepared(address: String) {
        val contact = favourites.firstOrNull { it.owns(address) } ?: recents.firstOrNull { it.owns(address) }
        contact?.let { c -> chosen.removeAll { c.owns(it) } }
        val key = Payload.recipientKey(address)
        if (chosen.none { Payload.recipientKey(it) == key }) chosen += address
    }
    fun finishSelection(request: PendingRecipient, address: String) {
        if (request.select) {
            request.replace?.let { c -> chosen.removeAll { c.owns(it) } }
            addPrepared(address)
        }
        if (request.pin) onPin(address, request.name)
    }
    fun select(request: PendingRecipient) {
        val prepared = if (request.phoneOnly && '@' in request.address) null else Payload.prepareRecipient(request.address)
        if (prepared == null) correcting = request else finishSelection(request, prepared)
    }
    fun commitTyped(): Boolean {
        val addresses = typed.split(',', ';').map(String::trim).filter(String::isNotEmpty)
        val prepared = addresses.map(Payload::prepareRecipient)
        val invalid = addresses.filterIndexed { index, _ -> prepared[index] == null }
        if (invalid.isNotEmpty()) {
            inputError = "Correct ${invalid.joinToString("; ")}. Use an international phone number with country code (for example +44 7700-900123), or an e-mail address. Separate recipients with commas or semicolons."
            return false
        }
        prepared.filterNotNull().forEach(::addPrepared)
        typed = ""
        inputError = null
        return true
    }

    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uri = res.data?.data ?: return@rememberLauncherForActivityResult
        ctx.contentResolver.query(uri, arrayOf(ContactsContract.Data.DATA1, ContactsContract.Data.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val address = c.getString(0) ?: return@use
                val name = c.getString(1) ?: ""
                select(PendingRecipient(address, name, phoneOnly = pickingPhone, pin = true))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("New message") },
                colors = ybTopBarColors(),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                actions = {
                    TextButton(
                        enabled = correcting == null && (chosen.isNotEmpty() || group || typed.isNotBlank()),
                        onClick = {
                            if (correcting == null && commitTyped()) {
                                if (chosen.isNotEmpty() || group) onNext(chosen.toList(), group)
                                else inputError = "Add an e-mail address or international phone number, or select My group."
                            }
                        },
                        colors = ButtonDefaults.textButtonColors(contentColor = OnYellow, disabledContentColor = OnYellow.copy(alpha = 0.38f)),
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
                    value = typed, onValueChange = { typed = it; inputError = null },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("To: e-mail or phone, or pick below") },
                    isError = inputError != null,
                    supportingText = { Text(inputError ?: "Use a country code for phones. Separate recipients with commas or semicolons.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitTyped() }),
                )
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        pickingPhone = false
                        pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Email.CONTENT_URI))
                    }) { Text("E-mail from contacts") }
                    OutlinedButton(onClick = {
                        pickingPhone = true
                        pickContact.launch(Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI))
                    }) { Text("Phone from contacts") }
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
                    val picked = chosen.any(c::owns)
                    ContactRow(c, selected = picked, onClick = {
                        if (picked) chosen.removeAll { c.owns(it) } else select(PendingRecipient(c.address, c.name))
                    },
                        star = true, onStar = { onUnpin(c.address) })
                    // A merged contact can be reached two ways; offer the alternates explicitly.
                    if (c.aliases.isNotEmpty()) Row(Modifier.padding(start = 56.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        c.addresses.forEach { a ->
                            val selected = chosen.any { Payload.recipientKey(it) == Payload.recipientKey(a) }
                            InputChip(selected = selected, onClick = {
                                if (selected) chosen.removeAll { Payload.recipientKey(it) == Payload.recipientKey(a) }
                                else select(PendingRecipient(a, c.name, replace = c))
                            },
                                label = { Text(channelName(a), style = MaterialTheme.typography.labelSmall) },
                                leadingIcon = { Icon(channelIcon(a), null, Modifier.width(14.dp)) })
                        }
                    }
                }
                if (recents.isNotEmpty()) item { SectionHeader("Recent") }
                items(recents, key = { "r" + it.address }) { c ->
                    val picked = chosen.any(c::owns)
                    ContactRow(c, selected = picked, onClick = {
                        if (picked) chosen.removeAll { c.owns(it) } else select(PendingRecipient(c.address, c.name))
                    },
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
            confirmButton = { TextButton(onClick = {
                select(PendingRecipient(c.address, name.trim(), pin = true, select = false))
                naming = null
            }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("Cancel") } },
        )
    }

    correcting?.let { request ->
        RecipientCorrectionDialog(
            address = request.address, name = request.name, phoneOnly = request.phoneOnly,
            onConfirm = { prepared -> finishSelection(request, prepared); correcting = null },
            onDismiss = { correcting = null },
        )
    }
}

@Composable
internal fun RecipientCorrectionDialog(
    address: String,
    name: String,
    phoneOnly: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var edited by rememberSaveable(address, name, phoneOnly) { mutableStateOf(address) }
    val prepared = if (phoneOnly && '@' in edited) null else Payload.prepareRecipient(edited)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Correct recipient") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (name.isNotBlank()) Text(name)
                Text("Use an international phone number with country code, for example +44 7700-900123. Remove parentheses; do not use a local leading 0 or a 00 prefix.")
                OutlinedTextField(
                    value = edited, onValueChange = { edited = it },
                    label = { Text(if (phoneOnly) "International phone number" else "E-mail or international phone") },
                    isError = prepared == null,
                    supportingText = { Text(prepared?.let { "Send to $it" } ?: "Correct the address before continuing.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = if (phoneOnly) KeyboardType.Phone else KeyboardType.Email),
                )
            }
        },
        confirmButton = { TextButton(enabled = prepared != null, onClick = { prepared?.let(onConfirm) }) { Text("Use recipient") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
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
