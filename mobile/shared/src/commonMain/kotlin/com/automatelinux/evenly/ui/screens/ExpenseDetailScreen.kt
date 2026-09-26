package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.decodeImage
import com.automatelinux.evenly.data.model.*
import com.automatelinux.evenly.data.rememberResource
import com.automatelinux.evenly.data.rememberWriteAction
import com.automatelinux.evenly.nav.Screen
import com.automatelinux.evenly.ui.components.*
import com.automatelinux.evenly.ui.theme.Evenly
import com.automatelinux.evenly.ui.theme.MoneyLarge
import com.automatelinux.evenly.ui.theme.MoneySmall
import com.automatelinux.evenly.util.PAYMENT_CATEGORY
import com.automatelinux.evenly.util.category
import com.automatelinux.evenly.util.formatAmount
import com.automatelinux.evenly.util.prettyDate
import com.automatelinux.evenly.util.relativeTime
import io.ktor.http.HttpMethod

@Composable
fun ExpenseDetailScreen(expenseId: Int) {
    val app = LocalApp.current
    val nav = app.nav
    val res = rememberResource(app.api, "/api/expenses/$expenseId", Expense.serializer())
    val action = rememberWriteAction()
    var confirmDelete by remember { mutableStateOf(false) }
    var receiptMenu by remember { mutableStateOf(false) }
    LaunchedEffect(res.data) { res.data?.let { e -> app.learn(e.comments.map { it.user }); app.learn(e.history.map { it.actor }) } }

    Column(Modifier.fillMaxSize()) {
        val e = res.data
        BackTopBar(if (e?.isPayment == true) "Payment" else "Expense", onBack = { nav.back() }) {
            if (e != null && e.deletedAt == null) {
                IconButton(onClick = { nav.push(Screen.EditExpense(expenseId = e.id)) }, modifier = Modifier.testTag("edit-expense")) { Icon(Icons.Outlined.Edit, "Edit") }
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete-expense")) { Icon(Icons.Outlined.Delete, "Delete") }
            }
        }
        OfflineBanner(res)
        RefreshBox(res) {
            Loaded(res) { e ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                    if (e.deletedAt != null) {
                        EvenlyCard(Modifier.padding(bottom = 12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.DeleteOutline, null, tint = Evenly.money.owe)
                                Spacer(Modifier.width(10.dp))
                                Text("This expense was deleted ${relativeTime(e.deletedAt)}", Modifier.weight(1f))
                                Button(onClick = {
                                    action.run {
                                        res.set(app.api.sendEmpty(HttpMethod.Post, "/api/expenses/${e.id}/restore", Expense.serializer()))
                                        res.refresh()
                                    }
                                }, modifier = Modifier.testTag("restore-expense")) { Text("Restore") }
                            }
                        }
                    }
                    Header(e)
                    SectionHeader("Who paid, who owes")
                    EvenlyCard(padding = PaddingValues(vertical = 6.dp)) {
                        e.shares.filter { it.paid > 0 || it.owed > 0 }.forEach { s -> ShareRow(e, s) }
                    }
                    if (!e.notes.isNullOrBlank()) {
                        SectionHeader("Notes")
                        EvenlyCard { Text(e.notes, style = MaterialTheme.typography.bodyLarge) }
                    }
                    if (!e.isPayment) {
                        SectionHeader("Receipt")
                        Receipt(e, onAttach = { receiptMenu = true })
                    }
                    SectionHeader("Comments")
                    Comments(e) { res.refresh() }
                    if (e.history.isNotEmpty()) {
                        SectionHeader("History")
                        EvenlyCard(padding = PaddingValues(vertical = 6.dp)) {
                            e.history.forEach { h ->
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                                    Avatar(h.actor, 28.dp)
                                    Spacer(Modifier.width(10.dp))
                                    Column {
                                        Text(h.text, style = MaterialTheme.typography.bodyMedium)
                                        Text(relativeTime(h.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(40.dp))
                }
            }
        }
    }

    if (receiptMenu) AlertDialog(
        onDismissRequest = { receiptMenu = false },
        title = { Text("Add a receipt") },
        text = {
            Column {
                listOf(true to "Take a photo", false to "Choose from gallery").forEach { (camera, label) ->
                    Row(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                            receiptMenu = false
                            app.platform.pickImage(camera) { file ->
                                if (file != null) action.run {
                                    res.set(app.api.upload("/api/expenses/$expenseId/receipt", file.fileName, file.mime, file.bytes, Expense.serializer()))
                                    res.refresh()
                                }
                            }
                        }.testTag(if (camera) "receipt-camera" else "receipt-gallery").padding(vertical = 14.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(if (camera) Icons.Outlined.PhotoCamera else Icons.Outlined.PhotoLibrary, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { receiptMenu = false }, modifier = Modifier.testTag("receipt-cancel")) { Text("Cancel") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this ${if (res.data?.isPayment == true) "payment" else "expense"}?") },
        text = { Text("It disappears from balances. You can restore it later from Activity.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                action.run {
                    app.api.sendEmpty(HttpMethod.Delete, "/api/expenses/$expenseId", OkResponse.serializer())
                    nav.back()
                }
            }, modifier = Modifier.testTag("delete-expense-confirm")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("delete-expense-cancel")) { Text("Cancel") } },
    )
    WriteErrorDialog(action)
}

@Composable
private fun Header(e: Expense) {
    val app = LocalApp.current
    val cat = if (e.isPayment) PAYMENT_CATEGORY else category(e.category)
    EvenlyCard(padding = PaddingValues(20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(cat, 60.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (e.isPayment) paymentSentence(app, e) else e.description,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Left, modifier = Modifier.fillMaxWidth(),
                )
                Text(cat.label, style = MaterialTheme.typography.bodyMedium, color = cat.color)
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(formatAmount(e.cost, e.currency), style = MoneyLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            buildString {
                append(prettyDate(e.date))
                if (e.repeat != "none") append(" · repeats ${e.repeat}")
                e.createdBy?.let { append(" · added by ${app.fullName(it)}") }
                e.updatedBy?.let { append(" · edited by ${app.fullName(it)}") }
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ShareRow(e: Expense, s: Share) {
    val app = LocalApp.current
    val name = app.fullName(s.userId)
    val verb = if (s.userId == app.meId) "" else "s"
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(app.user(s.userId), 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (s.paid > 0) Text("$name paid ${formatAmount(s.paid, e.currency)}", style = MaterialTheme.typography.bodyLarge)
            if (s.owed > 0) Text(
                if (e.isPayment) "$name received ${formatAmount(s.owed, e.currency)}" else "$name owe$verb ${formatAmount(s.owed, e.currency)}",
                style = if (s.paid > 0) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = if (s.paid > 0) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
        val net = s.paid - s.owed
        if (!e.isPayment && net != 0L) {
            Text(
                (if (net > 0) "+" else "−") + formatAmount(net, e.currency),
                style = MoneySmall, color = if (net > 0) Evenly.money.owed else Evenly.money.owe,
            )
        }
    }
}

@Composable
private fun Receipt(e: Expense, onAttach: () -> Unit) {
    val app = LocalApp.current
    var image by remember(e.receiptUrl) { mutableStateOf<ImageBitmap?>(null) }
    var failed by remember(e.receiptUrl) { mutableStateOf(false) }
    var zoom by remember { mutableStateOf(false) }
    LaunchedEffect(e.receiptUrl) {
        val url = e.receiptUrl ?: return@LaunchedEffect
        try { image = decodeImage(app.api.bytes(url)); failed = image == null } catch (_: Exception) { failed = true }
    }
    EvenlyCard(padding = PaddingValues(12.dp)) {
        when {
            e.receiptUrl == null -> Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onAttach).testTag("attach-receipt").padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.AddAPhoto, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Attach a receipt photo", color = MaterialTheme.colorScheme.primary)
            }
            image != null -> Column {
                Image(
                    image!!, "Receipt", contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(14.dp)).clickable { zoom = true }.testTag("receipt-image"),
                )
                TextButton(onClick = onAttach, modifier = Modifier.testTag("replace-receipt")) { Text("Replace receipt") }
            }
            failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Couldn't load the receipt", Modifier.weight(1f), color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = onAttach, modifier = Modifier.testTag("replace-receipt")) { Text("Replace") }
            }
            else -> SkeletonBlock(height = 220.dp, shape = RoundedCornerShape(14.dp))
        }
    }
    if (zoom && image != null) Dialog(onDismissRequest = { zoom = false }) {
        Image(image!!, "Receipt", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { zoom = false }.testTag("receipt-zoomed"))
    }
}

@Composable
private fun Comments(e: Expense, onPosted: () -> Unit) {
    val app = LocalApp.current
    val action = rememberWriteAction()
    var text by remember { mutableStateOf("") }
    EvenlyCard(padding = PaddingValues(vertical = 8.dp)) {
        if (e.comments.isEmpty()) Text("No comments yet", Modifier.padding(horizontal = 16.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        e.comments.forEach { c ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.Top) {
                Avatar(c.user, 32.dp)
                Spacer(Modifier.width(10.dp))
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 16.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row {
                        Text(if (c.user.id == app.meId) "You" else c.user.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        Text(relativeTime(c.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(c.body, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                text, { text = it }, placeholder = { Text("Add a comment") },
                shape = RoundedCornerShape(20.dp), modifier = Modifier.weight(1f).testTag("comment-input"), maxLines = 4,
            )
            Spacer(Modifier.width(6.dp))
            FilledIconButton(
                enabled = text.isNotBlank() && !action.busy,
                onClick = {
                    val body = text.trim()
                    action.run {
                        app.api.send(HttpMethod.Post, "/api/expenses/${e.id}/comments", CommentInput(body), CommentInput.serializer(), Comment.serializer())
                        text = ""
                        onPosted()
                    }
                },
                modifier = Modifier.testTag("comment-send"),
            ) { Icon(Icons.AutoMirrored.Outlined.Send, "Send") }
        }
    }
    WriteErrorDialog(action, "Comment not posted")
}
