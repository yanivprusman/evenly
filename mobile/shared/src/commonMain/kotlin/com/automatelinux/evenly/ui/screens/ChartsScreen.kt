package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PieChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.model.GroupDetail
import com.automatelinux.evenly.data.model.Stats
import com.automatelinux.evenly.data.rememberResource
import com.automatelinux.evenly.ui.components.*
import com.automatelinux.evenly.ui.theme.MoneyLarge
import com.automatelinux.evenly.ui.theme.MoneyMedium
import com.automatelinux.evenly.ui.theme.MoneySmall
import com.automatelinux.evenly.util.category
import com.automatelinux.evenly.util.formatAmount
import com.automatelinux.evenly.util.shortMonthLabel

@Composable
fun ChartsScreen(groupId: Int) {
    val app = LocalApp.current
    val group = rememberResource(app.api, "/api/groups/$groupId", GroupDetail.serializer())
    val g = group.data
    val currencies = remember(g) {
        val used = g?.expenses?.filter { !it.isPayment && it.deletedAt == null }?.map { it.currency }?.distinct() ?: emptyList()
        (listOfNotNull(g?.group?.defaultCurrency) + used).distinct()
    }
    var currency by remember(g?.group?.defaultCurrency) { mutableStateOf(g?.group?.defaultCurrency ?: app.defaultCurrency) }

    Column(Modifier.fillMaxSize()) {
        BackTopBar("Totals", onBack = { app.nav.back() }, subtitle = g?.group?.name)
        if (g == null) {
            Loaded(group) {}
            return@Column
        }
        val stats = rememberResource(app.api, "/api/stats?groupId=$groupId&currency=$currency", Stats.serializer())
        OfflineBanner(stats)
        RefreshBox(stats) {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
                if (currencies.size > 1) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        currencies.forEach { c ->
                            FilterChip(currency == c, { currency = c }, label = { Text(c) }, modifier = Modifier.testTag("stats-currency-$c"))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val groupSpend = g.expenses.filter { !it.isPayment && it.deletedAt == null && it.currency == currency }.sumOf { it.cost }
                Loaded(stats, skeleton = { SkeletonBlock(height = 240.dp, shape = RoundedCornerShape(20.dp)) }) { s ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TotalTile("Group spending", formatAmount(groupSpend, currency), Modifier.weight(1f))
                        TotalTile("Your share", formatAmount(s.total, currency), Modifier.weight(1f))
                    }
                    if (s.byCategory.isEmpty()) {
                        EmptyState(Icons.Outlined.PieChart, "Nothing to chart yet", "Once you have a share in some expenses, you'll see where the money goes.")
                    } else {
                        SectionHeader("Your share by category")
                        EvenlyCard { CategoryDonut(s, currency) }
                        SectionHeader("Your share by month")
                        EvenlyCard { MonthBars(s, currency) }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun TotalTile(label: String, value: String, modifier: Modifier) {
    EvenlyCard(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(value, style = MoneyMedium)
    }
}

@Composable
private fun CategoryDonut(s: Stats, currency: String) {
    val rows = s.byCategory.filter { it.amount > 0 }.sortedByDescending { it.amount }
    val total = rows.sumOf { it.amount }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(180.dp)) {
            val stroke = 26.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            var start = -90f
            val gap = if (rows.size > 1) 2.5f else 0f
            rows.forEach { r ->
                val sweep = 360f * r.amount / total
                drawArc(
                    category(r.category).color, start + gap / 2, (sweep - gap).coerceAtLeast(0.5f), false,
                    Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Butt),
                )
                start += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Your share", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatAmount(s.total, currency), style = MoneyMedium)
        }
    }
    Spacer(Modifier.height(12.dp))
    rows.forEach { r ->
        val c = category(r.category)
        val frac = r.amount.toFloat() / total
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryBadge(c, 32.dp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row {
                    Text(c.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(formatAmount(r.amount, currency), style = MoneySmall)
                }
                Spacer(Modifier.height(4.dp))
                Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(track)) {
                    Box(Modifier.fillMaxWidth(frac).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c.color))
                }
            }
            Text("${(frac * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
        }
    }
}

@Composable
private fun MonthBars(s: Stats, currency: String) {
    val months = s.byMonth.sortedBy { it.month }.takeLast(12)
    val max = (months.maxOfOrNull { it.amount } ?: 0L).coerceAtLeast(1)
    val bar = MaterialTheme.colorScheme.primary
    val latest = months.lastOrNull()
    if (latest != null) {
        Text("${shortMonthLabel(latest.month)}: ${formatAmount(latest.amount, currency)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
    }
    Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        months.forEach { m ->
            Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                val f = (m.amount.toFloat() / max).coerceIn(0.02f, 1f)
                Box(
                    Modifier.fillMaxWidth().fillMaxHeight(f * 0.85f)
                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                        .background(if (m == latest) bar else bar.copy(alpha = 0.45f)),
                )
                Spacer(Modifier.height(4.dp))
                Text(shortMonthLabel(m.month).take(3), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
