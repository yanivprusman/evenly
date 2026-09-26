package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.model.*
import com.automatelinux.evenly.data.rememberResource
import com.automatelinux.evenly.data.rememberWriteAction
import com.automatelinux.evenly.nav.Screen
import com.automatelinux.evenly.nav.Tab
import com.automatelinux.evenly.ui.components.*
import com.automatelinux.evenly.ui.theme.Evenly
import com.automatelinux.evenly.util.format
import com.automatelinux.evenly.util.isolate
import com.automatelinux.evenly.util.monthHeader
import com.automatelinux.evenly.util.nonZero
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import kotlinx.coroutines.delay
import kotlinx.serialization.builtins.ListSerializer

@Composable
fun FriendDetailScreen(friendId: Int) {
    val app = LocalApp.current
    val nav = app.nav
    val res = rememberResource(app.api, "/api/friends/$friendId", FriendDetail.serializer())
    val groupNames = rememberResource(app.api, "/api/groups", ListSerializer(GroupSummary.serializer()))
    val action = rememberWriteAction()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    LaunchedEffect(res.data) { res.data?.let { app.learn(it.friend) } }
    LaunchedEffect(groupNames.data) { groupNames.data?.forEach { app.learn(it.group.members) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = { AddExpenseFab { nav.push(Screen.EditExpense(friendId = friendId)) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            BackTopBar(res.data?.friend?.name ?: "Friend", onBack = { nav.back() }) {
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("friend-menu")) { Icon(Icons.Outlined.MoreVert, "More") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(text = { Text("Export CSV") }, onClick = { menu = false; exporting = true }, modifier = Modifier.testTag("friend-export"))
                        DropdownMenuItem(text = { Text("Remove friend") }, onClick = { menu = false; confirmDelete = true }, modifier = Modifier.testTag("friend-delete"))
                    }
                }
            }
            OfflineBanner(res)
            RefreshBox(res) {
                Loaded(res) { d ->
                    val first = d.friend.name.substringBefore(' ')
                    val sorted = d.expenses.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
                    val names = groupNames.data?.associate { it.group.id to it.group.name } ?: emptyMap()
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
                        item {
                            Column(
                                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                    .background(Evenly.money.hero).padding(20.dp),
                            ) {
                                val on = Evenly.money.onHero
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(d.friend, 52.dp)
                                    Spacer(Modifier.width(14.dp))
                                    Column {
                                        Text(d.friend.name, style = MaterialTheme.typography.titleLarge, color = on)
                                        Text(d.friend.phone ?: d.friend.email ?: "", style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.7f))
                                    }
                                }
                                Spacer(Modifier.height(12.dp))
                                val nz = d.net.nonZero()
                                if (nz.isEmpty()) Text("You and $first are settled up", style = MaterialTheme.typography.titleMedium, color = on)
                                nz.forEach { m ->
                                    Text((if (m.amount > 0) "$first owes you " else "You owe $first ") + m.format(), style = MaterialTheme.typography.titleLarge, color = on)
                                }
                                d.byGroup.filter { it.net.nonZero().isNotEmpty() }.forEach { gn ->
                                    gn.net.nonZero().forEach { m ->
                                        val where = if (gn.groupId == null) "Non-group" else "In ${isolate(gn.name)}"
                                        Text("$where: " + (if (m.amount > 0) "$first owes you " else "you owe $first ") + m.format(),
                                            style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.85f))
                                    }
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionChip("Settle up", Icons.Outlined.Handshake, "friend-settle-up", primary = true) {
                                    val m = d.net.nonZero().singleOrNull()
                                    nav.push(
                                        if (m == null) Screen.SettleUp(friendId = friendId)
                                        else if (m.amount < 0) Screen.SettleUp(friendId = friendId, from = app.meId, to = friendId, amount = -m.amount, currency = m.currency)
                                        else Screen.SettleUp(friendId = friendId, from = friendId, to = app.meId, amount = m.amount, currency = m.currency)
                                    )
                                }
                                d.net.nonZero().firstOrNull { it.amount > 0 }?.let { owed ->
                                    RemindButton(d.friend, owed, null, "friend-remind")
                                }
                                ActionChip("Export", Icons.Outlined.FileDownload, "friend-export-chip") { exporting = true }
                            }
                        }
                        if (sorted.isEmpty()) item {
                            EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "Nothing shared yet", "Add an expense with $first — in a group or just the two of you.", "Add expense", "empty-add-expense") {
                                nav.push(Screen.EditExpense(friendId = friendId))
                            }
                        }
                        sorted.groupBy { monthHeader(it.date) }.forEach { (month, list) ->
                            item(key = "h-$month") { SectionHeader(month, Modifier.padding(horizontal = 20.dp)) }
                            items(list, key = { it.id }) { e ->
                                Box(Modifier.padding(horizontal = 8.dp)) {
                                    ExpenseRow(e, subtitleExtra = e.groupId?.let { names[it] } ?: "non-group") { nav.push(Screen.ExpenseDetail(e.id)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (exporting) ExportSheet("/api/export.csv?friendId=$friendId", "evenly-${(res.data?.friend?.name ?: "friend").filter { it.isLetterOrDigit() }.ifEmpty { "friend" }}.csv") { exporting = false }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Remove ${res.data?.friend?.name ?: "friend"}?") },
        text = { Text("Only possible when you're settled up. Shared group history stays.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                action.run {
                    app.api.sendEmpty(HttpMethod.Delete, "/api/friends/$friendId", OkResponse.serializer())
                    nav.home(Tab.Friends)
                }
            }, modifier = Modifier.testTag("friend-delete-confirm")) { Text("Remove", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("friend-delete-cancel")) { Text("Cancel") } },
    )
    WriteErrorDialog(action, "Couldn't remove")
}

// ---------------------------------------------------------------- Search

@Composable
fun SearchScreen() {
    val app = LocalApp.current
    var q by rememberSaveableText()
    var results by remember { mutableStateOf<List<Expense>?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    val groups = rememberResource(app.api, "/api/groups", ListSerializer(GroupSummary.serializer()))
    val names = groups.data?.associate { it.group.id to it.group.name } ?: emptyMap()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) {
        if (q.isBlank()) { results = null; error = null; return@LaunchedEffect }
        delay(300)
        searching = true
        try {
            results = app.api.get("/api/search?q=${q.trim().encodeURLParameter()}", ListSerializer(Expense.serializer()))
            error = null
        } catch (e: Exception) {
            error = e.message
        } finally {
            searching = false
        }
    }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Search", onBack = { app.nav.back() })
        OutlinedTextField(
            q, { q = it },
            placeholder = { Text("Descriptions, notes, comments…") },
            leadingIcon = { Icon(Icons.Outlined.Search, null) },
            trailingIcon = { if (searching) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus).testTag("search-input"),
        )
        Spacer(Modifier.height(8.dp))
        val r = results
        when {
            error != null -> ErrorPanel(error!!) { val t = q; q = ""; q = t }
            r == null -> EmptyState(Icons.Outlined.Search, "Find any expense", "Try “שופרסל”, “ארנונה” or a word from a comment.")
            r.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "No matches", "Nothing mentions “${isolate(q.trim())}”.")
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(8.dp)) {
                items(r, key = { it.id }) { e ->
                    ExpenseRow(e, subtitleExtra = e.groupId?.let { names[it] } ?: "non-group") { app.nav.push(Screen.ExpenseDetail(e.id)) }
                }
            }
        }
    }
}
