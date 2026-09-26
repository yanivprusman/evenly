package com.automatelinux.evenly

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import com.automatelinux.evenly.data.Api
import com.automatelinux.evenly.data.AppContext
import com.automatelinux.evenly.data.LocalApp
import com.automatelinux.evenly.data.Platform
import com.automatelinux.evenly.data.ResponseCache
import com.automatelinux.evenly.nav.Navigator
import com.automatelinux.evenly.nav.Screen
import com.automatelinux.evenly.ui.screens.*
import com.automatelinux.evenly.ui.theme.AppTheme

/**
 * Shared entry composable. [backHandler] is supplied by the Android shell (BackHandler), since
 * system back is platform-specific.
 */
@Composable
fun App(
    baseUrl: String,
    token: String,
    cache: ResponseCache,
    platform: Platform,
    backHandler: @Composable (enabled: Boolean, onBack: () -> Unit) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val app = remember { AppContext(Api(baseUrl, token, cache), platform, Navigator(), scope) }
    LaunchedEffect(Unit) { app.load() }
    val nav = app.nav

    AppTheme {
        CompositionLocalProvider(LocalApp provides app) {
            backHandler(nav.canGoBack) { nav.back() }
            val saveable = rememberSaveableStateHolder()
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                AnimatedContent(
                    targetState = nav.current,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "screen",
                ) { screen ->
                    Box(Modifier.fillMaxSize()) {
                        saveable.SaveableStateProvider(screen.toString()) {
                            ScreenContent(screen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(screen: Screen) {
    when (screen) {
        Screen.Home -> HomeScreen()
        is Screen.GroupDetail -> GroupDetailScreen(screen.groupId)
        is Screen.GroupSettings -> GroupSettingsScreen(screen.groupId)
        Screen.CreateGroup -> CreateGroupScreen()
        is Screen.Balances -> BalancesScreen(screen.groupId)
        is Screen.Charts -> ChartsScreen(screen.groupId)
        is Screen.FriendDetail -> FriendDetailScreen(screen.friendId)
        is Screen.ExpenseDetail -> ExpenseDetailScreen(screen.expenseId)
        is Screen.EditExpense -> EditExpenseScreen(screen)
        is Screen.SettleUp -> SettleUpScreen(screen)
        Screen.Search -> SearchScreen()
    }
}
