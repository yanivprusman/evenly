package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.model.Dashboard
import com.automatelinux.evenly.data.model.Expense
import com.automatelinux.evenly.data.model.ExpenseInput
import com.automatelinux.evenly.data.model.User
import com.automatelinux.evenly.data.rememberResource
import com.automatelinux.evenly.data.rememberWriteAction
import com.automatelinux.evenly.nav.Screen
import com.automatelinux.evenly.ui.components.*
import com.automatelinux.evenly.ui.theme.Evenly
import com.automatelinux.evenly.ui.theme.MoneyLarge
import com.automatelinux.evenly.ui.theme.MoneySmall
import com.automatelinux.evenly.util.CATEGORIES
import com.automatelinux.evenly.util.CATEGORY_GROUPS
import com.automatelinux.evenly.util.CurrencyTable
import com.automatelinux.evenly.util.SplitType
import com.automatelinux.evenly.util.category
import com.automatelinux.evenly.util.formatAmount
import io.ktor.http.HttpMethod
import kotlin.math.abs

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditExpenseScreen(s: Screen.EditExpense) {
    val app = LocalApp.current
    val nav = app.nav
    val action = rememberWriteAction()
    val dash = rememberResource(app.api, "/api/dashboard", Dashboard.serializer())
    val existing = s.expenseId?.let { rememberResource(app.api, "/api/expenses/$it", Expense.serializer()) }
    val form = remember { ExpenseForm(app.meId, app.defaultCurrency) }
    var initialised by remember { mutableStateOf(s.expenseId == null) }
    var sheet by remember { mutableStateOf<String?>(null) }

    // Preselect the context we came from.
    LaunchedEffect(Unit) {
        if (s.expenseId == null) {
            if (s.groupId != null) form.groupId = s.groupId
            if (s.friendId != null) form.friendIds.add(s.friendId)
        }
    }
    // Edit mode: fill once, from the network copy (not a stale cache) when we can.
    LaunchedEffect(existing?.data, existing?.loading) {
        val e = existing?.data ?: return@LaunchedEffect
        if (!initialised && (existing.loading.not() || existing.offline)) {
            form.load(e)
            initialised = true
        }
    }
    val d = dash.data
    LaunchedEffect(d) { d?.let { app.learn(it.me); it.groups.forEach { g -> app.learn(g.group.members) }; app.learn(it.friends.map { f -> f.friend }) } }
    // Group default currency for a fresh expense.
    LaunchedEffect(form.groupId, d) {
        if (s.expenseId == null && form.amountText.isEmpty()) {
            d?.groups?.firstOrNull { it.group.id == form.groupId }?.group?.defaultCurrency?.let { form.currency = it }
        }
    }
    // Members follow the context.
    val contextMembers: List<Int> = form.groupId?.let { gid -> d?.groups?.firstOrNull { it.group.id == gid }?.group?.members?.map { it.id } }
        ?: form.friendIds.toList()
    LaunchedEffect(contextMembers, initialised) { if (initialised && (form.groupId == null || d != null)) form.syncMembers(contextMembers) }

    val problem = form.problem()

    Column(Modifier.fillMaxSize().imePadding()) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.back() }, modifier = Modifier.testTag("close-expense-form")) { Icon(Icons.Outlined.Close, "Close") }
            Text(if (s.expenseId == null) "Add expense" else "Edit expense", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).padding(start = 4.dp))
            Button(
                enabled = problem == null && !action.busy && initialised,
                onClick = {
                    val body = form.toInput()
                    action.run {
                        if (s.expenseId == null) app.api.send(HttpMethod.Post, "/api/expenses", body, ExpenseInput.serializer(), Expense.serializer(), keepNulls = true)
                        else app.api.send(HttpMethod.Patch, "/api/expenses/${s.expenseId}", body, ExpenseInput.serializer(), Expense.serializer(), keepNulls = true)
                        nav.back()
                    }
                },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.padding(end = 8.dp).testTag("save-expense"),
            ) { Text(if (action.busy) "Saving…" else "Save") }
        }
        if (!initialised) {
            if (existing?.error != null && existing.data == null) ErrorPanel(existing.error!!) { existing.refresh(true) } else SkeletonList(4, header = false)
            return@Column
        }

        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            WithWhom(form, d) { sheet = "context" }
            Spacer(Modifier.height(16.dp))

            EvenlyCard(padding = PaddingValues(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clip(RoundedCornerShape(14.dp)).clickable { sheet = "category" }.testTag("pick-category")) {
                        CategoryBadge(category(form.category), 52.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        form.description, { form.updateDescription(it) },
                        placeholder = { Text("What was it for?") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("expense-description"),
                    )
                }
                Text(
                    category(form.category).label + if (!form.categoryManual && form.category != "general") " · suggested" else "",
                    style = MaterialTheme.typography.labelMedium, color = category(form.category).color,
                    modifier = Modifier.padding(start = 4.dp, top = 6.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ValuePill(form.currency, "expense-currency") { sheet = "currency" }
                    Spacer(Modifier.width(12.dp))
                    OutlinedTextField(
                        form.amountText, { form.amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                        textStyle = MoneyLarge,
                        prefix = { Text(CurrencyTable.symbol(form.currency), style = MoneyLarge) },
                        placeholder = { Text("0.00", style = MoneyLarge) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f).testTag("expense-amount"),
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
            // "Paid by [you] and split [equally]"
            FlowRow(verticalArrangement = Arrangement.Center, horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                Text("Paid by", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterVertically))
                ValuePill(form.singlePayer?.let { if (it == app.meId) "you" else app.shortName(it) } ?: "multiple people", "pick-payer") { sheet = "payer" }
                Text("and split", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.align(Alignment.CenterVertically))
                ValuePill(if (form.splitType == SplitType.EQUAL && form.included.size == form.members.size) "equally" else form.splitType.label.lowercase(), "pick-split") { sheet = "split" }
            }
            SplitSummary(form)

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                DateField(form.date, "expense-date") { form.date = it }
                RepeatPill(form)
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                form.notes, { form.notes = it },
                label = { Text("Notes") },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.Notes, null) },
                shape = RoundedCornerShape(14.dp),
                minLines = 2,
                modifier = Modifier.fillMaxWidth().testTag("expense-notes"),
            )
            problem?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
            }
            Spacer(Modifier.height(48.dp))
        }
    }

    when (sheet) {
        "context" -> ContextSheet(form, d) { sheet = null }
        "category" -> CategorySheet(form) { sheet = null }
        "payer" -> PayerSheet(form) { sheet = null }
        "split" -> SplitSheet(form) { sheet = null }
        "currency" -> CurrencyPickerDialog(form.currency, { sheet = null }) { form.currency = it; sheet = null }
    }
    WriteErrorDialog(action, "Couldn't save the expense")
}

@Composable
private fun WithWhom(form: ExpenseForm, d: Dashboard?, onEdit: () -> Unit) {
    val app = LocalApp.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Evenly.money.card).clickable(onClick = onEdit)
            .testTag("pick-with-whom").padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("With you and:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(10.dp))
        val gid = form.groupId
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            when {
                gid != null -> {
                    val g = d?.groups?.firstOrNull { it.group.id == gid }?.group
                    Pill("All of ${g?.name ?: "group"}", MaterialTheme.colorScheme.onPrimaryContainer, MaterialTheme.colorScheme.primaryContainer)
                }
                form.friendIds.isEmpty() -> Text("Choose a group or friends", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                else -> form.friendIds.forEach { id ->
                    Pill(app.fullName(id), MaterialTheme.colorScheme.onSecondaryContainer, MaterialTheme.colorScheme.secondaryContainer)
                }
            }
        }
        Icon(Icons.Outlined.ExpandMore, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Live preview of who owes what, with the remainder while it doesn't add up. */
@Composable
private fun SplitSummary(form: ExpenseForm) {
    val app = LocalApp.current
    val cost = form.cost ?: return
    if (cost <= 0 || form.members.size < 2) return
    val r = form.split()
    val paid = form.paid()
    Spacer(Modifier.height(10.dp))
    EvenlyCard(padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)) {
        form.members.forEach { id ->
            val owed = r.owed[id] ?: 0L
            val p = paid?.first?.get(id) ?: 0L
            if (owed == 0L && p == 0L) return@forEach
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(app.user(id), 26.dp)
                Spacer(Modifier.width(10.dp))
                Text(app.fullName(id), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    (if (p > 0) "paid ${formatAmount(p, form.currency)} · " else "") + "owes ${formatAmount(owed, form.currency)}",
                    style = MoneySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        remainderLine(form, r.error, r.remainder, paid?.second)?.let { (text, bad) ->
            Text(text, style = MaterialTheme.typography.labelLarge, color = if (bad) Evenly.money.owe else Evenly.money.owed, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** "₪12.00 left" / "₪3.00 over" / "12.5% left"; null when everything adds up. */
private fun remainderLine(form: ExpenseForm, error: String?, remainder: Long, paidRemainder: Long?): Pair<String, Boolean>? {
    if (paidRemainder != null && paidRemainder != 0L) {
        val amt = formatAmount(abs(paidRemainder), form.currency)
        return (if (paidRemainder > 0) "$amt of the payment still unassigned" else "Payments are $amt over the total") to true
    }
    if (error == null) return null
    return when (form.splitType) {
        SplitType.EXACT -> if (remainder != 0L) (formatAmount(abs(remainder), form.currency) + if (remainder > 0) " left" else " over") to true else error to true
        SplitType.PERCENT -> if (remainder != 0L) {
            val pct = abs(remainder) / 100.0
            val txt = if (abs(remainder) % 100 == 0L) (abs(remainder) / 100).toString() else pct.toString()
            "$txt% ${if (remainder > 0) "left" else "over"}" to true
        } else error to true
        else -> error to true
    }
}

@Composable
private fun RepeatPill(form: ExpenseForm) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(50)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50))
                .clickable { open = true }.testTag("expense-repeat").padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Repeat, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(6.dp))
            Text(if (form.repeat == "none") "Doesn't repeat" else REPEATS.first { it.first == form.repeat }.second, style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(open, { open = false }) {
            REPEATS.forEach { (key, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { form.repeat = key; open = false }, modifier = Modifier.testTag("repeat-$key"))
            }
        }
    }
}

// ---------------------------------------------------------------- sheets

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContextSheet(form: ExpenseForm, d: Dashboard?, onDone: () -> Unit) {
    val app = LocalApp.current
    var adding by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        LazyColumn(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Text("With you and…", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)) }
            val groups = d?.groups?.filter { !it.group.archived } ?: emptyList()
            if (groups.isNotEmpty()) item { SectionHeader("A group") }
            items(groups, key = { "g" + it.group.id }) { g ->
                val on = form.groupId == g.group.id
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                        form.groupId = g.group.id; form.friendIds.clear(); onDone()
                    }.testTag("context-group-${g.group.id}").padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                        Icon(groupTypeIcon(g.group.type), null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(g.group.name, Modifier.weight(1f))
                    RadioButton(on, { form.groupId = g.group.id; form.friendIds.clear(); onDone() })
                }
            }
            item { SectionHeader("Or just friends (no group)") }
            items(d?.friends ?: emptyList(), key = { "f" + it.friend.id }) { f ->
                val on = form.groupId == null && f.friend.id in form.friendIds
                val toggle = {
                    form.groupId = null
                    if (on) form.friendIds.remove(f.friend.id) else form.friendIds.add(f.friend.id)
                }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { toggle() }
                        .testTag("context-friend-${f.friend.id}").padding(vertical = 6.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(f.friend, 36.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(f.friend.name, Modifier.weight(1f))
                    Checkbox(on, { toggle() })
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { adding = true }, modifier = Modifier.testTag("context-add-friend")) {
                        Icon(Icons.Outlined.PersonAdd, null); Spacer(Modifier.width(8.dp)); Text("New friend")
                    }
                    Button(onClick = onDone, modifier = Modifier.testTag("context-done")) { Text("Done") }
                }
            }
        }
    }
    if (adding) AddFriendDialog(onDismiss = { adding = false }) { u: User ->
        adding = false
        form.groupId = null
        form.friendIds.add(u.id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategorySheet(form: ExpenseForm, onDone: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDone) {
        LazyVerticalGrid(GridCells.Adaptive(96.dp), Modifier.fillMaxWidth().padding(horizontal = 12.dp), contentPadding = PaddingValues(bottom = 32.dp)) {
            val general = CATEGORIES.first { it.key == "general" }
            item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader("General") }
            item { CategoryCell(general, form.category == general.key) { form.category = general.key; form.categoryManual = true; onDone() } }
            CATEGORY_GROUPS.forEach { (title, prefix) ->
                item(span = { GridItemSpan(maxLineSpan) }) { SectionHeader(title) }
                items(CATEGORIES.filter { it.key.startsWith(prefix) }, key = { it.key }) { c ->
                    CategoryCell(c, form.category == c.key) { form.category = c.key; form.categoryManual = true; onDone() }
                }
            }
        }
    }
}

@Composable
private fun CategoryCell(c: com.automatelinux.evenly.util.Category, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.padding(4.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) c.color.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).testTag("category-${c.key}").padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CategoryBadge(c, 40.dp)
        Spacer(Modifier.height(6.dp))
        Text(c.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PayerSheet(form: ExpenseForm, onDone: () -> Unit) {
    val app = LocalApp.current
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 32.dp).verticalScroll(rememberScrollState())) {
            Text("Who paid?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp, bottom = 8.dp))
            form.members.forEach { id ->
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { form.singlePayer = id; onDone() }
                        .testTag("payer-$id").padding(vertical = 8.dp, horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(app.user(id), 36.dp); Spacer(Modifier.width(12.dp))
                    Text(app.fullName(id), Modifier.weight(1f))
                    RadioButton(form.singlePayer == id, { form.singlePayer = id; onDone() })
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { form.singlePayer = null }
                    .testTag("payer-multiple").padding(vertical = 10.dp, horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Groups, null, Modifier.size(36.dp).padding(6.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text("Multiple people", Modifier.weight(1f))
                RadioButton(form.singlePayer == null, { form.singlePayer = null })
            }
            if (form.singlePayer == null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                form.members.forEach { id ->
                    AmountRow(app.fullName(id), app.user(id), form.paidText[id] ?: "", CurrencyTable.symbol(form.currency), "paid-amount-$id") { form.paidText[id] = it }
                }
                val rem = form.paid()?.second
                Text(
                    when {
                        rem == null -> "Check the amounts"
                        rem == 0L -> "Adds up to the total ✓"
                        rem > 0 -> "${formatAmount(rem, form.currency)} left"
                        else -> "${formatAmount(-rem, form.currency)} over"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (rem == 0L) Evenly.money.owed else Evenly.money.owe,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
                Button(onClick = onDone, modifier = Modifier.align(Alignment.End).testTag("payer-done")) { Text("Done") }
            }
        }
    }
}

@Composable
private fun AmountRow(name: String, user: User?, value: String, prefix: String, tag: String, suffix: String? = null, trailingInfo: String? = null, onChange: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(user, 34.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (trailingInfo != null) Text(trailingInfo, style = MoneySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            value, { onChange(it.filter { c -> c.isDigit() || c == '.' || c == ',' || c == '-' }) },
            prefix = if (prefix.isNotEmpty()) ({ Text(prefix) }) else null,
            suffix = suffix?.let { { Text(it) } },
            singleLine = true,
            textStyle = MoneySmall.copy(textAlign = TextAlign.End),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.width(130.dp).testTag(tag),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SplitSheet(form: ExpenseForm, onDone: () -> Unit) {
    val app = LocalApp.current
    ModalBottomSheet(onDismissRequest = onDone, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("Split options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 20.dp, bottom = 8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SplitType.entries.forEach { t ->
                    FilterChip(form.splitType == t, { form.splitType = t }, label = { Text(t.label) }, modifier = Modifier.testTag("split-${t.key}"))
                }
            }
            Text(
                when (form.splitType) {
                    SplitType.EQUAL -> "Split equally between the people you tick."
                    SplitType.EXACT -> "Type exactly how much each person owes."
                    SplitType.PERCENT -> "Give each person a percentage; they must add up to 100%."
                    SplitType.SHARES -> "Split by shares — e.g. 2 shares for a couple, 1 for a single."
                    SplitType.ADJUSTMENT -> "Split equally, then add or subtract for anyone who had more or less."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            )
            val r = form.split()
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                form.members.forEach { id ->
                    val owedText = formatAmount(r.owed[id] ?: 0L, form.currency)
                    when (form.splitType) {
                        SplitType.EQUAL -> {
                            val on = id in form.included
                            Row(
                                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                                    .clickable { if (on) form.included.remove(id) else form.included.add(id) }
                                    .testTag("split-include-$id").padding(vertical = 6.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Avatar(app.user(id), 34.dp); Spacer(Modifier.width(10.dp))
                                Text(app.fullName(id), Modifier.weight(1f))
                                Text(if (on) owedText else "—", style = MoneySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Checkbox(on, { if (on) form.included.remove(id) else form.included.add(id) })
                            }
                        }
                        SplitType.EXACT -> AmountRow(app.fullName(id), app.user(id), form.inputText[id] ?: "", CurrencyTable.symbol(form.currency), "split-input-$id") { form.inputText[id] = it.replace("-", "") }
                        SplitType.PERCENT -> AmountRow(app.fullName(id), app.user(id), form.inputText[id] ?: "", "", "split-input-$id", suffix = "%", trailingInfo = owedText) { form.inputText[id] = it.replace("-", "") }
                        SplitType.SHARES -> AmountRow(app.fullName(id), app.user(id), form.inputText[id] ?: "", "", "split-input-$id", suffix = "sh", trailingInfo = owedText) { form.inputText[id] = it.replace("-", "") }
                        SplitType.ADJUSTMENT -> {
                            val on = id in form.included
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(on, { if (on) form.included.remove(id) else form.included.add(id) }, modifier = Modifier.testTag("split-include-$id"))
                                Box(Modifier.weight(1f)) {
                                    AmountRow(app.fullName(id), app.user(id), form.inputText[id] ?: "", "±", "split-input-$id", trailingInfo = if (on) owedText else "not included") { form.inputText[id] = it }
                                }
                            }
                        }
                    }
                }
            }
            val line = remainderLine(form, r.error, r.remainder, null)
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    line?.first ?: (form.cost?.let { "Adds up to ${formatAmount(it, form.currency)} ✓" } ?: "Enter the amount first"),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (line == null) Evenly.money.owed else Evenly.money.owe,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onDone, modifier = Modifier.testTag("split-done")) { Text("Done") }
            }
        }
    }
}
