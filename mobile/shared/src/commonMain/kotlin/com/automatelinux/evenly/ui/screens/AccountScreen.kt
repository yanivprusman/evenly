package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.model.PatchMeInput
import com.automatelinux.evenly.data.model.User
import com.automatelinux.evenly.data.rememberWriteAction
import com.automatelinux.evenly.ui.components.*
import io.ktor.http.HttpMethod
import kotlinx.coroutines.launch

@Composable
fun AccountTab() {
    val app = LocalApp.current
    val me = app.me
    var editing by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    val save = rememberWriteAction()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        HomeTopBar("Account")
        if (me == null) {
            if (app.meError != null) ErrorPanel(app.meError!!) { app.load() } else SkeletonList(3)
            return@Column
        }
        EvenlyCard(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), tag = "edit-profile", onClick = { editing = true }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(me, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(me.name, style = MaterialTheme.typography.titleLarge)
                    Text(me.email ?: "No email", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(me.phone ?: "No phone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(Icons.Outlined.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SectionHeader("Preferences", Modifier.padding(horizontal = 16.dp))
        EvenlyCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 4.dp)) {
            SettingRow(Icons.Outlined.Payments, "Default currency", app.defaultCurrency, "default-currency") { pickCurrency = true }
        }
        SectionHeader("Your data", Modifier.padding(horizontal = 16.dp))
        EvenlyCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 4.dp)) {
            SettingRow(Icons.Outlined.FileDownload, "Export all data (CSV)", "Every expense in every group", "export-all") { exporting = true }
        }
        SectionHeader("About", Modifier.padding(horizontal = 16.dp))
        EvenlyCard(Modifier.padding(horizontal = 16.dp), padding = PaddingValues(vertical = 4.dp)) {
            SettingRow(Icons.Outlined.Info, "Evenly", "Version ${app.platform.appVersion}", "about", onClick = null)
            SettingRow(Icons.Outlined.AllInclusive, "No limits", "Unlimited expenses, every day, free", "no-limits", onClick = null)
        }
        Spacer(Modifier.height(32.dp))
    }

    if (editing && me != null) EditProfileDialog(me) { editing = false }
    if (pickCurrency) CurrencyPickerDialog(app.defaultCurrency, onDismiss = { pickCurrency = false }) { code ->
        pickCurrency = false
        save.run {
            app.me = app.api.send(HttpMethod.Patch, "/api/me", PatchMeInput(defaultCurrency = code), PatchMeInput.serializer(), User.serializer())
        }
    }
    if (exporting) ExportSheet("/api/export.csv", "evenly-all.csv") { exporting = false }
    WriteErrorDialog(save)
}

@Composable
fun SettingRow(icon: ImageVector, title: String, value: String?, tag: String, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .testTag(tag).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (value != null) Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EditProfileDialog(me: User, onDone: () -> Unit) {
    val app = LocalApp.current
    val action = rememberWriteAction()
    var name by remember { mutableStateOf(me.name) }
    var email by remember { mutableStateOf(me.email ?: "") }
    var phone by remember { mutableStateOf(me.phone ?: "") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Your details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("me-name"))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("me-email"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("me-phone"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                action.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(enabled = name.isNotBlank() && !action.busy, modifier = Modifier.testTag("me-save"), onClick = {
                action.run {
                    app.me = app.api.send(HttpMethod.Patch, "/api/me",
                        PatchMeInput(name = name.trim(), email = email.trim(), phone = phone.trim()),
                        PatchMeInput.serializer(), User.serializer())
                    onDone()
                }
            }) { Text(if (action.busy) "Saving…" else if (action.error != null) "Retry" else "Save") }
        },
        dismissButton = { TextButton(onClick = onDone, modifier = Modifier.testTag("me-cancel")) { Text("Cancel") } },
    )
}

@Composable
fun CurrencyPickerDialog(current: String, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val app = LocalApp.current
    var q by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column {
                OutlinedTextField(q, { q = it }, placeholder = { Text("Search") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("currency-search"))
                Spacer(Modifier.height(8.dp))
                val list = app.currencies.filter { q.isBlank() || it.code.contains(q, true) || it.name.contains(q, true) }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    items(list, key = { it.code }) { c ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onPick(c.code) }.testTag("currency-${c.code}").padding(vertical = 12.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(c.symbol.trim(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(48.dp))
                            Column(Modifier.weight(1f)) {
                                Text(c.code, style = MaterialTheme.typography.bodyLarge)
                                Text(c.name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (c.code == current) Icon(Icons.Outlined.Check, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("currency-close")) { Text("Close") } },
    )
}

/** Downloads a CSV from [path] and lets the user save it to Downloads or share it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(path: String, fileName: String, onDismiss: () -> Unit) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var attempt by remember { mutableStateOf(0) }
    LaunchedEffect(attempt) {
        error = null
        try { bytes = app.api.bytes(path) } catch (e: Exception) { error = e.message }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            Text("Export CSV", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(fileName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            val b = bytes
            when {
                error != null -> {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { attempt++ }, modifier = Modifier.testTag("export-retry")) { Text("Retry") }
                }
                b == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Preparing your file…")
                }
                else -> {
                    Button(onClick = {
                        scope.launch { result = runCatching { app.platform.saveToDownloads(fileName, "text/csv", b) }.getOrElse { it.message ?: "Couldn't save" } }
                    }, modifier = Modifier.fillMaxWidth().testTag("export-save")) {
                        Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.width(8.dp)); Text("Save to Downloads")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { app.platform.shareFile(fileName, "text/csv", b) }, modifier = Modifier.fillMaxWidth().testTag("export-share")) {
                        Icon(Icons.Outlined.Share, null); Spacer(Modifier.width(8.dp)); Text("Share…")
                    }
                    result?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
