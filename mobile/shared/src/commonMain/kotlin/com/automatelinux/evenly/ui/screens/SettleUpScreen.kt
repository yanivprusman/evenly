package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.*
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.model.*
import com.automatelinux.evenly.data.rememberResource
import com.automatelinux.evenly.data.rememberWriteAction
import com.automatelinux.evenly.nav.Screen
import com.automatelinux.evenly.ui.components.*
import com.automatelinux.evenly.ui.theme.MoneyLarge
import com.automatelinux.evenly.util.CurrencyTable
import com.automatelinux.evenly.util.amountToInput
import com.automatelinux.evenly.util.formatAmount
import com.automatelinux.evenly.util.nonZero
import com.automatelinux.evenly.util.parseAmount
import com.automatelinux.evenly.util.today
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer

@Composable
fun SettleUpScreen(s: Screen.SettleUp) {
    val app = LocalApp.current
    val nav = app.nav
    val action = rememberWriteAction()

    // Who can take part, and which debts to suggest.
    val group = s.groupId?.let { rememberResource(app.api, "/api/groups/$it", GroupDetail.serializer()) }
    val friend = s.friendId?.let { rememberResource(app.api, "/api/friends/$it", FriendDetail.serializer()) }
    val groups = rememberResource(app.api, "/api/groups", ListSerializer(GroupSummary.serializer()))
    val people: List<User> = group?.data?.group?.members
        ?: listOfNotNull(app.me, friend?.data?.friend)
    LaunchedEffect(people) { app.learn(people) }
    val suggestions: List<Debt> = group?.data?.debts?.filter { it.from == app.meId || it.to == app.meId }
        ?: friend?.data?.net?.nonZero()?.map { m ->
            if (m.amount < 0) Debt(app.meId, s.friendId!!, -m.amount, m.currency) else Debt(s.friendId!!, app.meId, m.amount, m.currency)
        } ?: emptyList()

    var from by remember { mutableStateOf(s.from ?: app.meId) }
    var to by remember { mutableStateOf(s.to ?: s.friendId ?: -1) }
    var amountText by remember { mutableStateOf(s.amount?.let { amountToInput(it, s.currency ?: "ILS") } ?: "") }
    var currency by remember { mutableStateOf(s.currency ?: group?.data?.group?.defaultCurrency ?: app.defaultCurrency) }
    var date by remember { mutableStateOf(today()) }
    var pickCurrency by remember { mutableStateOf(false) }
    // For a friend payment: which group it is recorded in (null = non-group).
    var inGroup by remember { mutableStateOf(s.groupId) }
    var groupChosen by remember { mutableStateOf(s.groupId != null) }

    // Friend: when exactly one group holds the balance, record the payment there by default.
    LaunchedEffect(friend?.data) {
        val d = friend?.data ?: return@LaunchedEffect
        if (!groupChosen) {
            val open = d.byGroup.filter { it.net.nonZero().isNotEmpty() }
            if (open.size == 1) inGroup = open[0].groupId
        }
    }
    // Group: pick the other side when only two people and none was given.
    LaunchedEffect(people) {
        if (to == -1 || to == from) people.firstOrNull { it.id != from }?.let { to = it.id }
    }

    val amount = parseAmount(amountText, currency)
    val problem = when {
        to == -1 -> "Choose who gets paid"
        from == to -> "Payer and receiver must be different people"
        from != app.meId && to != app.meId -> "One side of the payment must be you"
        amount == null || amount <= 0 -> "Enter an amount"
        else -> null
    }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Settle up", onBack = { nav.back() })
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            if (suggestions.isNotEmpty()) {
                SectionHeader("Suggested")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    suggestions.forEach { d ->
                        val label = if (d.from == app.meId) "You → ${app.shortName(d.to)} ${formatAmount(d.amount, d.currency)}"
                        else "${app.shortName(d.from)} → you ${formatAmount(d.amount, d.currency)}"
                        FilterChip(
                            selected = from == d.from && to == d.to && amount == d.amount && currency == d.currency,
                            onClick = { from = d.from; to = d.to; currency = d.currency; amountText = amountToInput(d.amount, d.currency) },
                            label = { Text(label) },
                            modifier = Modifier.testTag("suggest-${d.from}-${d.to}-${d.currency}"),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                PersonPicker(people, from, "payer") { from = it }
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 10.dp)) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = { val t = from; from = to; to = t }, modifier = Modifier.testTag("swap-payer")) {
                        Icon(Icons.Outlined.SwapHoriz, "Swap", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                PersonPicker(people, to, "receiver") { to = it }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "${app.fullName(from)} paid ${if (to == -1) "…" else app.fullName(to)}",
                style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                ValuePill(currency, "settle-currency") { pickCurrency = true }
                Spacer(Modifier.width(10.dp))
                OutlinedTextField(
                    amountText, { amountText = it.filter { c -> c.isDigit() || c == '.' || c == ',' } },
                    textStyle = MoneyLarge.copy(textAlign = TextAlign.Center),
                    placeholder = { Text("0.00", style = MoneyLarge, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    prefix = { Text(CurrencyTable.symbol(currency), style = MoneyLarge) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.width(220.dp).testTag("settle-amount"),
                )
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                DateField(date, "settle-date") { date = it }
            }
            if (s.friendId != null && s.groupId == null) {
                SectionHeader("Record in")
                val options = listOf<Pair<Int?, String>>(null to "Non-group") +
                    (friend?.data?.byGroup?.filter { it.groupId != null }?.map { it.groupId to it.name } ?: emptyList())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    options.forEach { (gid, name) ->
                        FilterChip(inGroup == gid, { inGroup = gid; groupChosen = true }, label = { Text(name) }, modifier = Modifier.testTag("settle-in-${gid ?: "none"}"))
                    }
                }
            }
            Spacer(Modifier.height(28.dp))
            problem?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
            }
            Button(
                enabled = problem == null && !action.busy,
                onClick = {
                    val cost = amount ?: return@Button
                    action.run {
                        val body = ExpenseInput(
                            groupId = inGroup, description = "Payment", cost = cost, currency = currency,
                            date = date.toString(), category = "general", notes = null, isPayment = true,
                            splitType = "exact", repeat = "none",
                            shares = listOf(Share(from, paid = cost, owed = 0, input = 0.0), Share(to, paid = 0, owed = cost, input = cost.toDouble())),
                        )
                        app.api.send(HttpMethod.Post, "/api/expenses", body, ExpenseInput.serializer(), Expense.serializer(), keepNulls = true)
                        nav.back()
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp).testTag("record-payment"),
            ) { Text(if (action.busy) "Recording…" else "Record payment") }
            Spacer(Modifier.height(40.dp))
        }
    }
    if (pickCurrency) CurrencyPickerDialog(currency, { pickCurrency = false }) { currency = it; pickCurrency = false }
    WriteErrorDialog(action, "Couldn't record the payment")
}

@Composable
private fun PersonPicker(people: List<User>, selected: Int, tag: String, onPick: (Int) -> Unit) {
    val app = LocalApp.current
    var open by remember { mutableStateOf(false) }
    Box {
        Column(
            Modifier.clip(RoundedCornerShape(16.dp)).clickable { open = true }.testTag(tag).padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Avatar(app.user(selected) ?: people.firstOrNull { it.id == selected }, 72.dp)
            Spacer(Modifier.height(6.dp))
            Text(if (selected == -1) "Choose" else app.shortName(selected), style = MaterialTheme.typography.labelLarge)
        }
        DropdownMenu(open, { open = false }) {
            people.forEach { u ->
                DropdownMenuItem(
                    text = { Text(if (u.id == app.meId) "You" else u.name) },
                    leadingIcon = { Avatar(u, 28.dp) },
                    onClick = { onPick(u.id); open = false },
                    modifier = Modifier.testTag("$tag-option-${u.id}"),
                )
            }
        }
    }
}
