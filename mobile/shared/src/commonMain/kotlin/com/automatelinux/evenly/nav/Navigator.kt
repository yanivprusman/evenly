package com.automatelinux.evenly.nav

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class Tab(val label: String) { Groups("Groups"), Friends("Friends"), Activity("Activity"), Account("Account") }

sealed interface Screen {
    data object Home : Screen
    data class GroupDetail(val groupId: Int) : Screen
    data class GroupSettings(val groupId: Int) : Screen
    data object CreateGroup : Screen
    data class Balances(val groupId: Int) : Screen
    data class Charts(val groupId: Int) : Screen
    data class FriendDetail(val friendId: Int) : Screen
    data class ExpenseDetail(val expenseId: Int) : Screen
    /** Add (expenseId == null) or edit. groupId / friendId preselect "with you and". */
    data class EditExpense(val expenseId: Int? = null, val groupId: Int? = null, val friendId: Int? = null) : Screen
    data class SettleUp(
        val groupId: Int? = null,
        val friendId: Int? = null,
        val from: Int? = null,
        val to: Int? = null,
        val amount: Long? = null,
        val currency: String? = null,
    ) : Screen
    data object Search : Screen
}

class Navigator {
    val stack = mutableStateListOf<Screen>(Screen.Home)
    var tab by mutableStateOf(Tab.Groups)

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1 || tab != Tab.Groups

    fun push(s: Screen) { stack.add(s) }

    fun back() {
        if (stack.size > 1) stack.removeAt(stack.lastIndex)
        else if (tab != Tab.Groups) tab = Tab.Groups
    }

    /** Replace the top screen (e.g. after creating something, show it instead of the form). */
    fun replace(s: Screen) {
        stack.removeAt(stack.lastIndex)
        stack.add(s)
    }

    fun home(t: Tab) {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
        tab = t
    }
}
