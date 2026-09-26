package com.automatelinux.evenly.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.model.Activity
import com.automatelinux.evenly.data.rememberResource
import com.automatelinux.evenly.nav.Screen
import com.automatelinux.evenly.ui.components.*
import com.automatelinux.evenly.ui.theme.Evenly
import com.automatelinux.evenly.ui.theme.MoneySmall
import com.automatelinux.evenly.util.format
import com.automatelinux.evenly.util.relativeTime
import kotlinx.coroutines.launch
import kotlinx.serialization.builtins.ListSerializer

private const val PAGE = 50

@Composable
fun ActivityTab() {
    val app = LocalApp.current
    val first = rememberResource(app.api, "/api/activity?limit=$PAGE", ListSerializer(Activity.serializer()))
    val older = remember { mutableStateListOf<Activity>() }
    var loadingMore by remember { mutableStateOf(false) }
    var reachedEnd by remember { mutableStateOf(false) }
    var moreError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    // A refresh of the first page resets the older pages.
    LaunchedEffect(first.data) {
        older.clear()
        reachedEnd = (first.data?.size ?: 0) < PAGE
        first.data?.forEach { app.learn(it.actor) }
    }

    fun loadMore() {
        val all = (first.data ?: return) + older
        if (loadingMore || reachedEnd || all.isEmpty()) return
        loadingMore = true
        moreError = null
        scope.launch {
            try {
                val page = app.api.get("/api/activity?before=${all.last().id}&limit=$PAGE", ListSerializer(Activity.serializer()))
                older.addAll(page)
                if (page.size < PAGE) reachedEnd = true
            } catch (e: Exception) {
                moreError = e.message
            } finally {
                loadingMore = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        HomeTopBar("Activity")
        OfflineBanner(first)
        RefreshBox(first) {
            Loaded(first, skeleton = { SkeletonList(header = false) }) { items ->
                val all = items + older
                if (all.isEmpty()) {
                    EmptyState(Icons.Outlined.Bolt, "Nothing here yet", "Every expense, edit, payment and comment in your groups shows up here.")
                } else {
                    val nearEnd by remember { derivedStateOf { (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= all.size - 5 } }
                    LaunchedEffect(nearEnd, all.size) { if (nearEnd) loadMore() }
                    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                        items(all, key = { it.id }) { a -> ActivityRow(a) }
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                when {
                                    loadingMore -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                    moreError != null -> TextButton(onClick = { loadMore() }, modifier = Modifier.testTag("activity-load-more-retry")) { Text("Couldn't load more · Retry") }
                                    reachedEnd -> Text("That's everything", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityRow(a: Activity) {
    val app = LocalApp.current
    val clickable = a.expenseId != null || a.groupId != null
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (clickable) Modifier.clickable {
                if (a.expenseId != null) app.nav.push(Screen.ExpenseDetail(a.expenseId))
                else if (a.groupId != null) app.nav.push(Screen.GroupDetail(a.groupId))
            } else Modifier)
            .testTag("activity-${a.id}")
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(a.actor, 40.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(a.text, style = MaterialTheme.typography.bodyMedium)
            a.amountForMe?.takeIf { it.amount != 0L }?.let { m ->
                val good = m.amount > 0
                Text(
                    (if (good) "You get back " else "You owe ") + m.format(),
                    style = MoneySmall,
                    color = if (good) Evenly.money.owed else Evenly.money.owe,
                )
            }
            Text(relativeTime(a.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
