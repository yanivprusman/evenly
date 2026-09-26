package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.Resource
import com.automatelinux.evenly.data.model.Dashboard
import com.automatelinux.evenly.data.model.Debt
import com.automatelinux.evenly.data.model.FriendSummary
import com.automatelinux.evenly.data.model.GroupSummary
import com.automatelinux.evenly.data.model.Money
import com.automatelinux.evenly.data.rememberResource
import com.automatelinux.evenly.nav.Screen
import com.automatelinux.evenly.nav.Tab
import com.automatelinux.evenly.ui.components.*
import com.automatelinux.evenly.ui.theme.Evenly
import com.automatelinux.evenly.ui.theme.MoneyLarge
import com.automatelinux.evenly.util.format
import com.automatelinux.evenly.util.isolate
import com.automatelinux.evenly.util.nonZero
import com.automatelinux.evenly.util.parseInstant
import kotlinx.datetime.Clock
import kotlin.time.Duration.Companion.days

fun groupTypeIcon(type: String): ImageVector = when (type) {
    "home" -> Icons.Outlined.Home
    "trip" -> Icons.Outlined.Luggage
    "couple" -> Icons.Outlined.FavoriteBorder
    else -> Icons.Outlined.Groups
}

@Composable
fun HomeScreen() {
    val app = LocalApp.current
    val nav = app.nav
    val dash = rememberResource(app.api, "/api/dashboard", Dashboard.serializer())
    LaunchedEffect(dash.data) {
        dash.data?.let { d ->
            app.learn(d.me)
            d.groups.forEach { app.learn(it.group.members) }
            app.learn(d.friends.map { it.friend })
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0),
        bottomBar = { BottomBar(nav.tab) { nav.tab = it } },
        floatingActionButton = {
            if (nav.tab == Tab.Groups || nav.tab == Tab.Friends) AddExpenseFab { nav.push(Screen.EditExpense()) }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (nav.tab) {
                Tab.Groups -> GroupsTab(dash)
                Tab.Friends -> FriendsTab(dash)
                Tab.Activity -> ActivityTab()
                Tab.Account -> AccountTab()
            }
        }
    }
}

@Composable
fun AddExpenseFab(onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, null) },
        text = { Text("Add expense") },
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("add-expense"),
    )
}

@Composable
private fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    NavigationBar(containerColor = Evenly.money.card, tonalElevation = 0.dp) {
        Tab.entries.forEach { t ->
            val selected = t == current
            val icon = when (t) {
                Tab.Groups -> if (selected) Icons.Filled.Groups else Icons.Outlined.Groups
                Tab.Friends -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                Tab.Activity -> if (selected) Icons.Filled.Bolt else Icons.Outlined.Bolt
                Tab.Account -> if (selected) Icons.Filled.AccountCircle else Icons.Outlined.AccountCircle
            }
            NavigationBarItem(
                selected = selected,
                onClick = { onSelect(t) },
                icon = { Icon(icon, null) },
                label = { Text(t.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                ),
                modifier = Modifier.testTag("tab-${t.label.lowercase()}")
                    .then(if (selected) Modifier.semantics { contentDescription = "active-tab:${t.label}" } else Modifier),
            )
        }
    }
}

@Composable
fun HomeTopBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
        actions()
    }
}

/** The ink band at the top of Groups / Friends: "Overall, you owe ₪19,951.65". */
@Composable
fun OverallHero(total: List<Money>, caption: String) {
    val nz = total.nonZero()
    Column(
        Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Evenly.money.hero).padding(horizontal = 20.dp, vertical = 18.dp),
    ) {
        val on = Evenly.money.onHero
        if (nz.isEmpty()) {
            Text("Overall", style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.75f))
            Text("You're all settled up", style = MaterialTheme.typography.headlineSmall, color = on)
        } else {
            nz.forEachIndexed { i, m ->
                val owe = m.amount < 0
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (owe) Evenly.money.owe else Evenly.money.owed))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        (if (i == 0) "Overall, " else "and ") + if (owe) "you owe" else "you are owed",
                        style = MaterialTheme.typography.labelLarge, color = on.copy(alpha = 0.8f),
                    )
                }
                Text(m.format(), style = MoneyLarge, color = on)
                if (i < nz.lastIndex) Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(caption, style = MaterialTheme.typography.bodySmall, color = on.copy(alpha = 0.65f))
    }
}

@Composable
fun GroupsTab(dash: Resource<Dashboard>) {
    val app = LocalApp.current
    val nav = app.nav
    var showSettled by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        HomeTopBar("Groups") {
            IconButton(onClick = { nav.push(Screen.Search) }, modifier = Modifier.testTag("search")) { Icon(Icons.Outlined.Search, "Search") }
            IconButton(onClick = { nav.push(Screen.CreateGroup) }, modifier = Modifier.testTag("create-group")) { Icon(Icons.Outlined.GroupAdd, "Create group") }
        }
        OfflineBanner(dash)
        RefreshBox(dash) {
            Loaded(dash) { d ->
                val now = Clock.System.now()
                val (active, collapsed) = d.groups
                    .sortedByDescending { parseInstant(it.lastActivityAt) ?: kotlinx.datetime.Instant.DISTANT_PAST }
                    .partition { s ->
                        val old = (parseInstant(s.lastActivityAt)?.let { now - it > 30.days }) ?: true
                        !(s.group.archived || (s.myNet.nonZero().isEmpty() && old))
                    }
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        OverallHero(d.total, "Across ${d.groups.size} group${if (d.groups.size == 1) "" else "s"} and ${d.friends.size} friend${if (d.friends.size == 1) "" else "s"}")
                        Spacer(Modifier.height(12.dp))
                    }
                    if (d.groups.isEmpty()) item {
                        EmptyState(Icons.Outlined.Groups, "No groups yet", "Make one for your home, a trip or a couple — then add expenses as they happen.", "Create a group", "empty-create-group") { nav.push(Screen.CreateGroup) }
                    }
                    items(active, key = { it.group.id }) { s -> GroupCard(s) }
                    if (collapsed.isNotEmpty()) {
                        item {
                            TextButton(
                                onClick = { showSettled = !showSettled },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).testTag("toggle-settled-groups"),
                            ) {
                                Text(if (showSettled) "Hide settled-up groups" else "Show ${collapsed.size} settled-up group${if (collapsed.size == 1) "" else "s"}")
                            }
                        }
                        if (showSettled) items(collapsed, key = { "c" + it.group.id }) { s -> GroupCard(s) }
                    }
                }
            }
        }
    }
}

@Composable
private fun debtLine(d: Debt, meId: Int): Pair<String, Boolean> {
    val app = LocalApp.current
    return if (d.from == meId) "You owe ${app.shortName(d.to)} ${Money(d.amount, d.currency).format()}" to false
    else "${app.shortName(d.from)} owes you ${Money(d.amount, d.currency).format()}" to true
}

@Composable
private fun GroupCard(s: GroupSummary) {
    val app = LocalApp.current
    val g = s.group
    EvenlyCard(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        tag = "group-${g.id}",
        onClick = { app.nav.push(Screen.GroupDetail(g.id)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(groupTypeIcon(g.type), null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val lines = s.debts.filter { it.from == app.meId || it.to == app.meId }
                lines.take(2).forEach { d ->
                    val (text, good) = debtLine(d, app.meId)
                    Text(text, style = MaterialTheme.typography.bodySmall, color = if (good) Evenly.money.owed else Evenly.money.owe, maxLines = 1)
                }
                if (lines.size > 2) Text("+${lines.size - 2} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (g.archived) Text("Archived", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(8.dp))
            NetLines(s.myNet)
        }
    }
}

@Composable
fun FriendsTab(dash: Resource<Dashboard>) {
    val app = LocalApp.current
    val nav = app.nav
    var adding by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        HomeTopBar("Friends") {
            IconButton(onClick = { nav.push(Screen.Search) }, modifier = Modifier.testTag("search-friends")) { Icon(Icons.Outlined.Search, "Search") }
            IconButton(onClick = { adding = true }, modifier = Modifier.testTag("add-friend")) { Icon(Icons.Outlined.PersonAdd, "Add friend") }
        }
        OfflineBanner(dash)
        RefreshBox(dash) {
            Loaded(dash) { d ->
                val sorted = d.friends.sortedWith(compareBy<FriendSummary> { it.net.nonZero().isEmpty() }.thenBy { it.friend.name.lowercase() })
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        val open = d.friends.count { it.net.nonZero().isNotEmpty() }
                        OverallHero(d.total, if (open == 0) "Nobody owes anybody" else "$open open balance${if (open == 1) "" else "s"}")
                        Spacer(Modifier.height(12.dp))
                    }
                    if (d.friends.isEmpty()) item {
                        EmptyState(Icons.Outlined.PersonAdd, "No friends yet", "Add the people you share costs with. They don't need the app.", "Add a friend", "empty-add-friend") { adding = true }
                    }
                    items(sorted, key = { it.friend.id }) { f -> FriendCard(f) }
                }
            }
        }
    }
    if (adding) AddFriendDialog(onDismiss = { adding = false }, onAdded = { adding = false; dash.refresh() })
}

@Composable
private fun FriendCard(f: FriendSummary) {
    val app = LocalApp.current
    val first = f.friend.name.substringBefore(' ')
    EvenlyCard(
        Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
        tag = "friend-${f.friend.id}",
        onClick = { app.nav.push(Screen.FriendDetail(f.friend.id)) },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(f.friend, 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(f.friend.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // Per-group lines only add information when the balance is spread over several.
                val open = f.byGroup.filter { it.net.nonZero().isNotEmpty() }
                if (open.size > 1) open.take(3).forEach { gn ->
                    gn.net.nonZero().forEach { m ->
                        val where = if (gn.groupId == null) "Non-group" else "In ${isolate(gn.name)}"
                        val phrase = if (m.amount > 0) "$first owes you" else "you owe $first"
                        Text("$where: $phrase ${m.format()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            NetLines(f.net, who = first)
        }
    }
}

@Composable
fun AddFriendDialog(onDismiss: () -> Unit, onAdded: (com.automatelinux.evenly.data.model.User) -> Unit) {
    val app = LocalApp.current
    val action = com.automatelinux.evenly.data.rememberWriteAction()
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a friend") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("friend-name"))
                OutlinedTextField(phone, { phone = it }, label = { Text("Phone") }, supportingText = { Text("Used for WhatsApp reminders") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("friend-phone"),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Phone))
                OutlinedTextField(email, { email = it }, label = { Text("Email (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth().testTag("friend-email"),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Email))
                action.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && !action.busy,
                onClick = {
                    action.run {
                        val u = app.api.send(
                            io.ktor.http.HttpMethod.Post, "/api/friends",
                            com.automatelinux.evenly.data.model.FriendInput(name.trim(), email.trim().ifBlank { null }, phone.trim().ifBlank { null }),
                            com.automatelinux.evenly.data.model.FriendInput.serializer(),
                            com.automatelinux.evenly.data.model.User.serializer(),
                        )
                        app.learn(u)
                        onAdded(u)
                    }
                },
                modifier = Modifier.testTag("friend-save"),
            ) { Text(if (action.busy) "Adding…" else if (action.error != null) "Retry" else "Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("friend-cancel")) { Text("Cancel") } },
    )
}
