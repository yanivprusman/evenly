package com.automatelinux.evenly.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.AppContext
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.model.Expense
import com.automatelinux.evenly.ui.theme.Evenly
import com.automatelinux.evenly.ui.theme.MoneySmall
import com.automatelinux.evenly.util.PAYMENT_CATEGORY
import com.automatelinux.evenly.util.category
import com.automatelinux.evenly.util.formatAmount
import com.automatelinux.evenly.util.isolate
import com.automatelinux.evenly.util.monthShort
import com.automatelinux.evenly.util.parseDate

/** "Yaniv paid ₪240.00" / "2 people paid ₪240.00" */
fun paidSummary(app: AppContext, e: Expense): String {
    val payers = e.shares.filter { it.paid > 0 }
    val total = formatAmount(e.cost, e.currency)
    return when (payers.size) {
        0 -> "Nobody paid $total"
        1 -> "${app.shortName(payers[0].userId)} paid $total"
        else -> "${payers.size} people paid $total"
    }
}

/** "Yaniv paid Ayelet ₪5,000.00" */
fun paymentSentence(app: AppContext, e: Expense): String {
    val from = e.shares.firstOrNull { it.paid > 0 }?.userId
    val to = e.shares.firstOrNull { it.owed > 0 }?.userId
    val f = from?.let { app.shortName(it) } ?: "Someone"
    val t = to?.let { if (it == app.meId) "you" else app.shortName(it) } ?: "someone"
    return "$f paid $t ${formatAmount(e.cost, e.currency)}"
}

@Composable
fun ExpenseRow(e: Expense, subtitleExtra: String? = null, onRestore: (() -> Unit)? = null, onClick: () -> Unit) {
    val app = LocalApp.current
    val me = app.meId
    val my = e.shares.firstOrNull { it.userId == me }
    val net = my?.let { it.paid - it.owed } ?: 0L
    val date = parseDate(e.date)
    val deleted = e.deletedAt != null
    val cat = if (e.isPayment) PAYMENT_CATEGORY else category(e.category)

    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
            .testTag("expense-row-${e.id}").padding(horizontal = 8.dp, vertical = 10.dp)
            .alpha(if (deleted) 0.6f else 1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.width(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date?.let { monthShort(it.monthNumber).uppercase() } ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(date?.dayOfMonth?.toString() ?: "", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.width(10.dp))
        CategoryBadge(cat, 42.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            if (e.isPayment) {
                Text(
                    paymentSentence(app, e),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                if (subtitleExtra != null) Text(
                    isolate(subtitleExtra),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            } else {
                // Descriptions are often Hebrew in an English UI: keep them left-aligned next to
                // the icon, but let the text itself run right-to-left.
                Text(
                    e.description,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Left,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (deleted) TextDecoration.LineThrough else null,
                )
                Text(
                    paidSummary(app, e) + (subtitleExtra?.let { " · ${isolate(it)}" } ?: "") + (if (e.commentCount > 0) " · ${e.commentCount} comment${if (e.commentCount == 1) "" else "s"}" else ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(min = 76.dp)) {
            when {
                deleted -> {
                    Text("deleted", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (onRestore != null) androidx.compose.material3.TextButton(
                        onClick = onRestore,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp).testTag("restore-row-${e.id}"),
                    ) { Text("Restore") }
                }
                my == null -> Text("not involved", style = MaterialTheme.typography.labelMedium, color = Evenly.money.settled)
                e.isPayment -> {
                    val label = if (my.paid > 0) "you paid" else "you received"
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatAmount(e.cost, e.currency), style = MoneySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                net == 0L -> Text("no balance", style = MaterialTheme.typography.labelMedium, color = Evenly.money.settled)
                else -> {
                    val lent = net > 0
                    val color = if (lent) Evenly.money.owed else Evenly.money.owe
                    Text(if (lent) "you lent" else "you borrowed", style = MaterialTheme.typography.labelMedium, color = color)
                    Text(formatAmount(net, e.currency), style = MoneySmall, color = color)
                }
            }
        }
    }
}

/** Israeli 05X → 9725X; strips everything but digits. Null when unusable. */
fun whatsappNumber(phone: String?): String? {
    val d = phone?.filter { it.isDigit() } ?: return null
    if (d.length < 8) return null
    return when {
        d.startsWith("972") -> d
        d.startsWith("0") -> "972" + d.drop(1)
        else -> d
    }
}
