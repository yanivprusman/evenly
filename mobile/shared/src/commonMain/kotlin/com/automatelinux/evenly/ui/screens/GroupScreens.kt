package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.model.*
import com.automatelinux.evenly.data.rememberResource
import com.automatelinux.evenly.data.rememberWriteAction
import com.automatelinux.evenly.nav.Screen
import com.automatelinux.evenly.ui.components.*
import com.automatelinux.evenly.ui.theme.Evenly
import com.automatelinux.evenly.ui.theme.MoneyMedium
import com.automatelinux.evenly.util.format
import com.automatelinux.evenly.util.formatAmount
import com.automatelinux.evenly.util.monthHeader
import com.automatelinux.evenly.util.nonZero
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLParameter
import kotlinx.serialization.builtins.ListSerializer

val GROUP_TYPES = listOf("home" to "Home", "trip" to "Trip", "couple" to "Couple", "other" to "Other")

@Composable
fun GroupDetailScreen(groupId: Int) {
    val app = LocalApp.current
    val nav = app.nav
    val res = rememberResource(app.api, "/api/groups/$groupId", GroupDetail.serializer())
    var exporting by remember { mutableStateOf(false) }
    LaunchedEffect(res.data) { res.data?.let { app.learn(it.group.members) } }
    val restore = rememberWriteAction()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        floatingActionButton = { AddExpenseFab { nav.push(Screen.EditExpense(groupId = groupId)) } },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            BackTopBar(res.data?.group?.name ?: "Group", onBack = { nav.back() }) {
                IconButton(onClick = { nav.push(Screen.GroupSettings(groupId)) }, modifier = Modifier.testTag("group-settings")) {
                    Icon(Icons.Outlined.Settings, "Group settings")
                }
            }
            OfflineBanner(res)
            RefreshBox(res) {
                Loaded(res) { d ->
                    val mine = d.debts.filter { it.from == app.meId || it.to == app.meId }
                    val sorted = d.expenses.sortedWith(compareByDescending<Expense> { it.date }.thenByDescending { it.id })
                    val byMonth = sorted.groupBy { monthHeader(it.date) }
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 104.dp)) {
                        item {
                            GroupHeader(d, mine)
                            Row(
                                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                ActionChip("Settle up", Icons.Outlined.Handshake, "settle-up", primary = true) {
                                    val one = mine.singleOrNull()
                                    nav.push(
                                        if (one != null) Screen.SettleUp(groupId = groupId, from = one.from, to = one.to, amount = one.amount, currency = one.currency)
                                        else Screen.SettleUp(groupId = groupId)
                                    )
                                }
                                ActionChip("Balances", Icons.Outlined.Balance, "balances") { nav.push(Screen.Balances(groupId)) }
                                ActionChip("Totals", Icons.Outlined.PieChart, "totals") { nav.push(Screen.Charts(groupId)) }
                                ActionChip("Export", Icons.Outlined.FileDownload, "export") { exporting = true }
                            }
                        }
                        if (sorted.isEmpty()) item {
                            EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "No expenses yet", "Add the first one — groceries, rent, a bill. Evenly keeps the running balance.", "Add expense", "empty-add-expense") {
                                nav.push(Screen.EditExpense(groupId = groupId))
                            }
                        }
                        byMonth.forEach { (month, list) ->
                            item(key = "h-$month") { SectionHeader(month, Modifier.padding(horizontal = 20.dp)) }
                            items(list, key = { it.id }) { e ->
                                Box(Modifier.padding(horizontal = 8.dp)) {
                                    ExpenseRow(e, onRestore = { restore.run {
                                        app.api.sendEmpty(HttpMethod.Post, "/api/expenses/${e.id}/restore", Expense.serializer())
                                        res.refresh()
                                    } }) { nav.push(Screen.ExpenseDetail(e.id)) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    WriteErrorDialog(restore, "Couldn't restore")
    if (exporting) ExportSheet("/api/export.csv?groupId=$groupId", "evenly-${(res.data?.group?.name ?: "group").filter { it.isLetterOrDigit() }.ifEmpty { "group" }}.csv") { exporting = false }
}

@Composable
private fun GroupHeader(d: GroupDetail, mine: List<Debt>) {
    val app = LocalApp.current
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Evenly.money.hero).padding(20.dp),
    ) {
        val on = Evenly.money.onHero
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(groupTypeIcon(d.group.type), null, tint = on.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "${GROUP_TYPES.firstOrNull { it.first == d.group.type }?.second ?: "Group"} · ${d.group.members.size} people",
                style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f),
            )
        }
        Spacer(Modifier.height(10.dp))
        val nz = d.myNet.nonZero()
        if (nz.isEmpty()) {
            Text("You are all settled up in this group", style = MaterialTheme.typography.titleLarge, color = on)
        } else nz.forEach { m ->
            Text(
                (if (m.amount > 0) "You are owed " else "You owe ") + m.format(),
                style = MaterialTheme.typography.titleLarge, color = on,
            )
        }
        if (mine.isNotEmpty()) Spacer(Modifier.height(8.dp))
        mine.forEach { debt ->
            val good = debt.to == app.meId
            val text = if (good) "${app.shortName(debt.from)} owes you ${formatAmount(debt.amount, debt.currency)}"
            else "You owe ${app.shortName(debt.to)} ${formatAmount(debt.amount, debt.currency)}"
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Box(Modifier.size(7.dp).clip(RoundedCornerShape(50)).background(if (good) Evenly.money.owed else Evenly.money.owe))
                Spacer(Modifier.width(8.dp))
                Text(text, style = MaterialTheme.typography.bodyMedium, color = on.copy(alpha = 0.9f))
            }
        }
    }
}

// ---------------------------------------------------------------- Balances

@Composable
fun BalancesScreen(groupId: Int) {
    val app = LocalApp.current
    val nav = app.nav
    val res = rememberResource(app.api, "/api/groups/$groupId", GroupDetail.serializer())
    LaunchedEffect(res.data) { res.data?.let { app.learn(it.group.members) } }
    Column(Modifier.fillMaxSize()) {
        BackTopBar("Balances", onBack = { nav.back() }, subtitle = res.data?.group?.name)
        OfflineBanner(res)
        RefreshBox(res) {
            Loaded(res) { d ->
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp)) {
                    item { SectionHeader("Everyone's balance") }
                    item {
                        EvenlyCard(padding = PaddingValues(vertical = 6.dp)) {
                            d.group.members.forEach { u ->
                                val net = d.balances.firstOrNull { it.userId == u.id }?.net ?: emptyList()
                                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Avatar(u, 40.dp)
                                    Spacer(Modifier.width(12.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(if (u.id == app.meId) "${u.name} (you)" else u.name, style = MaterialTheme.typography.bodyLarge)
                                        val nz = net.nonZero()
                                        if (nz.isEmpty()) Text("settled up", style = MaterialTheme.typography.bodySmall, color = Evenly.money.settled)
                                        nz.forEach { m ->
                                            // From the group's point of view: positive = gets back.
                                            Text(
                                                (if (m.amount > 0) "gets back " else "owes ") + m.format(),
                                                style = MaterialTheme.typography.bodySmall,
                                                color = if (m.amount > 0) Evenly.money.owed else Evenly.money.owe,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    item { SectionHeader(if (d.group.simplifyDebts) "Suggested payments (simplified)" else "Suggested payments") }
                    if (d.debts.isEmpty()) item {
                        EvenlyCard { Text("Nobody owes anybody — all square.", style = MaterialTheme.typography.bodyLarge) }
                    }
                    items(d.debts) { debt -> DebtCard(d.group, debt) }
                }
            }
        }
    }
}

@Composable
private fun DebtCard(group: Group, debt: Debt) {
    val app = LocalApp.current
    val from = group.members.firstOrNull { it.id == debt.from }
    val to = group.members.firstOrNull { it.id == debt.to }
    EvenlyCard(Modifier.padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(from, 36.dp)
            Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, Modifier.padding(horizontal = 6.dp).size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Avatar(to, 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${app.shortName(debt.from)} ${if (debt.from == app.meId) "owe" else "owes"} ${app.shortName(debt.to)}", style = MaterialTheme.typography.bodyMedium)
                Text(formatAmount(debt.amount, debt.currency), style = MoneyMedium, color = if (debt.to == app.meId) Evenly.money.owed else if (debt.from == app.meId) Evenly.money.owe else MaterialTheme.colorScheme.onSurface)
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (debt.from == app.meId || debt.to == app.meId) {
                FilledTonalButton(
                    onClick = { app.nav.push(Screen.SettleUp(groupId = group.id, from = debt.from, to = debt.to, amount = debt.amount, currency = debt.currency)) },
                    modifier = Modifier.testTag("debt-settle-${debt.from}-${debt.to}"),
                ) { Text("Settle up") }
            }
            if (debt.to == app.meId) {
                RemindButton(from, Money(debt.amount, debt.currency), group.name, "debt-remind-${debt.from}")
            }
        }
    }
}

/** Opens WhatsApp with a friendly nudge pre-typed. */
@Composable
fun RemindButton(debtor: User?, amount: Money, context: String?, tag: String) {
    val app = LocalApp.current
    val number = whatsappNumber(debtor?.phone)
    var noPhone by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            if (number == null) { noPhone = true; return@OutlinedButton }
            val me = app.me?.name?.substringBefore(' ') ?: ""
            val where = context?.let { " in \"$it\"" } ?: ""
            val text = "Hi ${debtor?.name?.substringBefore(' ') ?: ""}! Friendly reminder from Evenly: you owe me ${amount.format(isolateBidi = false)}$where. Thanks! $me".trim()
            if (!app.platform.openUrl("https://wa.me/$number?text=${text.encodeURLParameter()}")) noPhone = true
        },
        modifier = Modifier.testTag(tag),
    ) {
        Icon(Icons.Outlined.NotificationsActive, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Remind")
    }
    if (noPhone) AlertDialog(
        onDismissRequest = { noPhone = false },
        title = { Text("No phone number") },
        text = { Text("Add a phone number for ${debtor?.name ?: "them"} to send a WhatsApp reminder (or WhatsApp isn't installed).") },
        confirmButton = { TextButton(onClick = { noPhone = false }, modifier = Modifier.testTag("remind-ok")) { Text("OK") } },
    )
}

// ---------------------------------------------------------------- Create group

@Composable
fun CreateGroupScreen() {
    val app = LocalApp.current
    val nav = app.nav
    val friends = rememberResource(app.api, "/api/friends", ListSerializer(FriendSummary.serializer()))
    val action = rememberWriteAction()
    var name by rememberSaveableText()
    var type by remember { mutableStateOf("home") }
    var simplify by remember { mutableStateOf(true) }
    var currency by remember { mutableStateOf(app.defaultCurrency) }
    val selected = remember { mutableStateListOf<Int>() }
    val extra = remember { mutableStateListOf<User>() }
    var addingPerson by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("New group", onBack = { nav.back() }) {
            TextButton(
                enabled = name.isNotBlank() && !action.busy,
                onClick = {
                    action.run {
                        val g = app.api.send(HttpMethod.Post, "/api/groups",
                            CreateGroupInput(name.trim(), type, selected.toList(), currency, simplify),
                            CreateGroupInput.serializer(), Group.serializer())
                        app.learn(g.members)
                        nav.replace(Screen.GroupDetail(g.id))
                    }
                },
                modifier = Modifier.testTag("create-group-save"),
            ) { Text(if (action.busy) "Saving…" else "Save") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Group name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("group-name"))
            SectionHeader("Type")
            TypePicker(type) { type = it }
            SectionHeader("Members")
            val all = (friends.data?.map { it.friend } ?: emptyList()) + extra.filter { e -> friends.data?.none { it.friend.id == e.id } ?: true }
            EvenlyCard(padding = PaddingValues(vertical = 4.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(app.me, 36.dp); Spacer(Modifier.width(12.dp)); Text("${app.me?.name ?: "You"} (you)", Modifier.weight(1f))
                }
                if (friends.data == null && friends.loading) Box(Modifier.padding(16.dp)) { SkeletonBlock(160.dp) }
                all.forEach { u ->
                    val on = u.id in selected
                    Row(
                        Modifier.fillMaxWidth().clickable { if (on) selected.remove(u.id) else selected.add(u.id) }
                            .testTag("member-toggle-${u.id}").padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Avatar(u, 36.dp); Spacer(Modifier.width(12.dp))
                        Text(u.name, Modifier.weight(1f))
                        Checkbox(on, { if (on) selected.remove(u.id) else selected.add(u.id) })
                    }
                }
                TextButton(onClick = { addingPerson = true }, modifier = Modifier.padding(horizontal = 8.dp).testTag("add-new-person")) {
                    Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Add someone new")
                }
            }
            SectionHeader("Settings")
            EvenlyCard(padding = PaddingValues(vertical = 4.dp)) {
                SwitchRow("Simplify group debts", "Fewer, larger payments to settle up", simplify, "simplify-toggle") { simplify = it }
                SettingRow(Icons.Outlined.Payments, "Default currency", currency, "group-currency") { pickCurrency = true }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (addingPerson) AddFriendDialog(onDismiss = { addingPerson = false }) { u ->
        addingPerson = false
        extra.add(u)
        selected.add(u.id)
    }
    if (pickCurrency) CurrencyPickerDialog(currency, { pickCurrency = false }) { currency = it; pickCurrency = false }
    WriteErrorDialog(action, "Couldn't create the group")
}

@Composable
fun rememberSaveableText(initial: String = "") = androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(initial) }

@Composable
fun TypePicker(type: String, onPick: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GROUP_TYPES.forEach { (key, label) ->
            FilterChip(
                selected = type == key,
                onClick = { onPick(key) },
                label = { Text(label) },
                leadingIcon = { Icon(groupTypeIcon(key), null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("type-$key"),
            )
        }
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.testTag(tag).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

// ---------------------------------------------------------------- Group settings

@Composable
fun GroupSettingsScreen(groupId: Int) {
    val app = LocalApp.current
    val nav = app.nav
    val res = rememberResource(app.api, "/api/groups/$groupId", GroupDetail.serializer())
    val action = rememberWriteAction()
    var renaming by remember { mutableStateOf(false) }
    var pickCurrency by remember { mutableStateOf(false) }
    var addMember by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var removeMember by remember { mutableStateOf<User?>(null) }

    fun patch(p: PatchGroupInput) = action.run {
        app.api.send(HttpMethod.Patch, "/api/groups/$groupId", p, PatchGroupInput.serializer(), Group.serializer())
        res.refresh()
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Group settings", onBack = { nav.back() }, subtitle = res.data?.group?.name)
        OfflineBanner(res)
        Loaded(res) { d ->
            val g = d.group
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                EvenlyCard(tag = "rename-group", onClick = { renaming = true }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(groupTypeIcon(g.type), null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(14.dp))
                        Text(g.name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.Edit, "Rename", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SectionHeader("Type")
                TypePicker(g.type) { patch(PatchGroupInput(type = it)) }
                SectionHeader("Members")
                EvenlyCard(padding = PaddingValues(vertical = 4.dp)) {
                    g.members.forEach { u ->
                        val net = d.balances.firstOrNull { it.userId == u.id }?.net ?: emptyList()
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(u, 38.dp); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(if (u.id == app.meId) "${u.name} (you)" else u.name, style = MaterialTheme.typography.bodyLarge)
                                Text(u.phone ?: u.email ?: if (u.registered) "Uses Evenly" else "Not on Evenly", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (u.id != app.meId) {
                                val canRemove = net.nonZero().isEmpty()
                                IconButton(
                                    onClick = { removeMember = u },
                                    enabled = canRemove,
                                    modifier = Modifier.testTag("remove-member-${u.id}"),
                                ) { Icon(Icons.Outlined.PersonRemove, if (canRemove) "Remove" else "Has a balance — settle first") }
                            }
                        }
                    }
                    TextButton(onClick = { addMember = true }, modifier = Modifier.padding(horizontal = 8.dp).testTag("add-member")) {
                        Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Add people")
                    }
                }
                SectionHeader("Settings")
                EvenlyCard(padding = PaddingValues(vertical = 4.dp)) {
                    SwitchRow("Simplify group debts", "Combine debts so fewer payments settle everyone", g.simplifyDebts, "simplify-toggle") { patch(PatchGroupInput(simplifyDebts = it)) }
                    SettingRow(Icons.Outlined.Payments, "Default currency", g.defaultCurrency, "group-currency") { pickCurrency = true }
                }
                SectionHeader("Danger zone")
                EvenlyCard(padding = PaddingValues(vertical = 4.dp)) {
                    SettingRow(Icons.Outlined.Archive, if (g.archived) "Unarchive group" else "Archive group",
                        if (g.archived) "Show it with your active groups again" else "Hide it from the list; nothing is deleted", "archive-group") {
                        patch(PatchGroupInput(archived = !g.archived))
                    }
                    val settled = d.balances.all { it.net.nonZero().isEmpty() }
                    SettingRow(Icons.Outlined.DeleteForever, "Delete group",
                        if (settled) "Permanently remove this group" else "Settle all balances first", "delete-group",
                        onClick = if (settled) ({ confirmDelete = true }) else null)
                }
                Spacer(Modifier.height(40.dp))
            }

            if (renaming) {
                var text by remember { mutableStateOf(g.name) }
                AlertDialog(
                    onDismissRequest = { renaming = false },
                    title = { Text("Rename group") },
                    text = { OutlinedTextField(text, { text = it }, singleLine = true, modifier = Modifier.testTag("rename-input")) },
                    confirmButton = { TextButton(enabled = text.isNotBlank(), onClick = { renaming = false; patch(PatchGroupInput(name = text.trim())) }, modifier = Modifier.testTag("rename-save")) { Text("Save") } },
                    dismissButton = { TextButton(onClick = { renaming = false }, modifier = Modifier.testTag("rename-cancel")) { Text("Cancel") } },
                )
            }
            if (pickCurrency) CurrencyPickerDialog(g.defaultCurrency, { pickCurrency = false }) { pickCurrency = false; patch(PatchGroupInput(defaultCurrency = it)) }
            if (addMember) AddMemberDialog(g, onDismiss = { addMember = false }) { body ->
                addMember = false
                action.run {
                    val ng = app.api.send(HttpMethod.Post, "/api/groups/$groupId/members", body, AddMemberInput.serializer(), Group.serializer())
                    app.learn(ng.members)
                    res.refresh()
                }
            }
            removeMember?.let { u ->
                AlertDialog(
                    onDismissRequest = { removeMember = null },
                    title = { Text("Remove ${u.name}?") },
                    text = { Text("Their past expenses stay in the group.") },
                    confirmButton = {
                        TextButton(onClick = {
                            removeMember = null
                            action.run {
                                app.api.sendEmpty(HttpMethod.Delete, "/api/groups/$groupId/members/${u.id}", Group.serializer())
                                res.refresh()
                            }
                        }, modifier = Modifier.testTag("remove-member-confirm")) { Text("Remove") }
                    },
                    dismissButton = { TextButton(onClick = { removeMember = null }, modifier = Modifier.testTag("remove-member-cancel")) { Text("Cancel") } },
                )
            }
            if (confirmDelete) AlertDialog(
                onDismissRequest = { confirmDelete = false },
                title = { Text("Delete ${g.name}?") },
                text = { Text("This removes the group and its history for everyone. It can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmDelete = false
                        action.run {
                            app.api.sendEmpty(HttpMethod.Delete, "/api/groups/$groupId", OkResponse.serializer())
                            nav.home(com.automatelinux.evenly.nav.Tab.Groups)
                        }
                    }, modifier = Modifier.testTag("delete-group-confirm")) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { confirmDelete = false }, modifier = Modifier.testTag("delete-group-cancel")) { Text("Cancel") } },
            )
        }
    }
    WriteErrorDialog(action)
}

@Composable
private fun AddMemberDialog(g: Group, onDismiss: () -> Unit, onAdd: (AddMemberInput) -> Unit) {
    val app = LocalApp.current
    val friends = rememberResource(app.api, "/api/friends", ListSerializer(FriendSummary.serializer()))
    var newMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to ${g.name}") },
        text = {
            if (!newMode) {
                val candidates = friends.data?.map { it.friend }?.filter { f -> g.members.none { it.id == f.id } } ?: emptyList()
                Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                    if (friends.data == null) SkeletonBlock(160.dp)
                    else if (candidates.isEmpty()) Text("All your friends are already here.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    candidates.forEach { u ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { onAdd(AddMemberInput(userId = u.id)) }
                                .testTag("add-member-${u.id}").padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) { Avatar(u, 36.dp); Spacer(Modifier.width(12.dp)); Text(u.name) }
                    }
                    TextButton(onClick = { newMode = true }, modifier = Modifier.testTag("add-member-new")) {
                        Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("Someone new")
                    }
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("new-member-name"))
                    OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("new-member-phone"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
                    OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("new-member-email"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
                }
            }
        },
        confirmButton = {
            if (newMode) TextButton(
                enabled = name.isNotBlank(),
                onClick = { onAdd(AddMemberInput(name = name.trim(), phone = phone.trim().ifBlank { null }, email = email.trim().ifBlank { null })) },
                modifier = Modifier.testTag("new-member-save"),
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("add-member-cancel")) { Text("Cancel") } },
    )
}
