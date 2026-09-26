package com.automatelinux.evenly.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.automatelinux.evenly.data.Resource
import com.automatelinux.evenly.data.WriteAction
import com.automatelinux.evenly.data.model.Money
import com.automatelinux.evenly.data.model.User
import com.automatelinux.evenly.ui.theme.Evenly
import com.automatelinux.evenly.ui.theme.MoneySmall
import com.automatelinux.evenly.util.Category
import com.automatelinux.evenly.util.format
import com.automatelinux.evenly.util.nonZero

fun parseHexColor(hex: String, fallback: Color = Color(0xFF5B7C8D)): Color {
    val h = hex.removePrefix("#")
    return h.toLongOrNull(16)?.let { if (h.length == 6) Color(0xFF000000 or it) else null } ?: fallback
}

fun initials(name: String): String {
    val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts[0].take(1).uppercase()
        else -> (parts[0].take(1) + parts[1].take(1)).uppercase()
    }
}

@Composable
fun Avatar(user: User?, size: Dp = 40.dp, modifier: Modifier = Modifier) {
    val bg = parseHexColor(user?.color ?: "#7C8784")
    Box(
        modifier.size(size).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            initials(user?.name ?: "?"),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.38f).sp,
        )
    }
}

@Composable
fun CategoryBadge(cat: Category, size: Dp = 44.dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(cat.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(cat.icon, contentDescription = cat.label, tint = cat.color, modifier = Modifier.size(size * 0.52f))
    }
}

/** A raised, rounded card. Pass [onClick] + [tag] for a tappable one. */
@Composable
fun EvenlyCard(
    modifier: Modifier = Modifier,
    tag: String? = null,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    var m = modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Evenly.money.card)
    if (onClick != null) m = m.clickable(onClick = onClick)
    if (tag != null) m = m.testTag(tag)
    Column(m.padding(padding), content = content)
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, bottom = 8.dp),
    )
}

/**
 * One coloured line per currency from MY point of view: "you owe ₪12.00" / "you are owed ₪12.00"
 * (or "X owes you" style when [who] is given). Zero → "settled up".
 */
@Composable
fun NetLines(
    net: List<Money>,
    who: String? = null,
    style: androidx.compose.ui.text.TextStyle = MoneySmall,
    align: Alignment.Horizontal = Alignment.End,
    settledText: String = "settled up",
) {
    val nz = net.nonZero()
    Column(horizontalAlignment = align) {
        if (nz.isEmpty()) {
            Text(settledText, style = MaterialTheme.typography.bodyMedium, color = Evenly.money.settled)
        }
        nz.forEach { m ->
            val positive = m.amount > 0
            val label = when {
                who == null && positive -> "you are owed"
                who == null -> "you owe"
                positive -> "$who owes you"
                else -> "you owe $who"
            }
            val color = if (positive) Evenly.money.owed else Evenly.money.owe
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(m.format(), style = style, color = color)
        }
    }
}

@Composable
fun BackTopBar(
    title: String,
    onBack: () -> Unit,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

@Composable
fun OfflineBanner(resource: Resource<*>) {
    if (!resource.offline) return
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { resource.refresh(byUser = true) }.testTag("offline-banner-retry")
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text("Offline · showing saved data", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text("Retry", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

/** Load state wrapper: skeleton on first load, error panel when nothing to show, else content. */
@Composable
fun <T> Loaded(
    resource: Resource<T>,
    skeleton: @Composable () -> Unit = { SkeletonList() },
    content: @Composable (T) -> Unit,
) {
    val data = resource.data
    when {
        data != null -> content(data)
        resource.loading -> skeleton()
        else -> ErrorPanel(resource.error ?: "Couldn't load", onRetry = { resource.refresh(byUser = true) })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(resource: Resource<*>, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    PullToRefreshBox(
        isRefreshing = resource.refreshing,
        onRefresh = { resource.refresh(byUser = true) },
        modifier = modifier.fillMaxSize(),
        content = content,
    )
}

@Composable
fun ErrorPanel(message: String, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Text(message, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag("retry-load")) { Text("Try again") }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, line: String, actionLabel: String? = null, actionTag: String = "empty-action", onAction: (() -> Unit)? = null) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(34.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAction, modifier = Modifier.testTag(actionTag)) { Text(actionLabel) }
        }
    }
}

@Composable
fun SkeletonBlock(width: Dp? = null, height: Dp = 14.dp, modifier: Modifier = Modifier, shape: RoundedCornerShape = RoundedCornerShape(7.dp)) {
    val t = rememberInfiniteTransition()
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(800), RepeatMode.Reverse))
    val m = if (width != null) modifier.width(width) else modifier.fillMaxWidth()
    Box(m.height(height).alpha(a).clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest))
}

@Composable
fun SkeletonList(rows: Int = 6, header: Boolean = true) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp), userScrollEnabled = false) {
        if (header) item {
            Spacer(Modifier.height(12.dp))
            SkeletonBlock(height = 96.dp, shape = RoundedCornerShape(20.dp))
            Spacer(Modifier.height(20.dp))
        }
        items(rows) {
            Row(Modifier.padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(44.dp, 44.dp, shape = RoundedCornerShape(14.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    SkeletonBlock(160.dp)
                    Spacer(Modifier.height(8.dp))
                    SkeletonBlock(100.dp, 11.dp)
                }
                SkeletonBlock(64.dp)
            }
        }
    }
}

/** Shows a write failure with Retry; nothing is dropped silently. */
@Composable
fun WriteErrorDialog(action: WriteAction, title: String = "Couldn't save") {
    val err = action.error ?: return
    AlertDialog(
        onDismissRequest = { action.dismiss() },
        title = { Text(title) },
        text = { Text(err) },
        confirmButton = { TextButton(onClick = { action.retry() }, modifier = Modifier.testTag("write-retry")) { Text("Retry") } },
        dismissButton = { TextButton(onClick = { action.dismiss() }, modifier = Modifier.testTag("write-dismiss")) { Text("Cancel") } },
    )
}

@Composable
fun Pill(text: String, color: Color, bg: Color, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    )
}

/** Tappable chip used for the group action row (Settle up · Balances · …). */
@Composable
fun ActionChip(label: String, icon: ImageVector, tag: String, primary: Boolean = false, onClick: () -> Unit) {
    val bg = if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerLowest
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Row(
        Modifier.clip(RoundedCornerShape(50))
            .then(if (!primary) Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(50)) else Modifier)
            .background(bg).clickable(onClick = onClick).testTag(tag)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg)
    }
}
