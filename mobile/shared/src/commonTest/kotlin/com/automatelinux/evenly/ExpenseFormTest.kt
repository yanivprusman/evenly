package com.automatelinux.evenly

import com.automatelinux.evenly.data.model.Expense
import com.automatelinux.evenly.data.model.Share
import com.automatelinux.evenly.ui.screens.ExpenseForm
import com.automatelinux.evenly.util.SplitType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ExpenseFormTest {
    private fun form(): ExpenseForm = ExpenseForm(me = 1, defaultCurrency = "ILS").apply {
        updateDescription("שופרסל")
        amountText = "100"
        groupId = 7
        syncMembers(listOf(1, 2, 3))
    }

    @Test fun suggestsCategoryFromHebrew() {
        assertEquals("food.groceries", form().category)
    }

    @Test fun newExpenseSplitsEquallyIncludingMe() {
        val owed = form().toInput().shares.associate { it.userId to it.owed }
        assertEquals(mapOf(1 to 3334L, 2 to 3333L, 3 to 3333L), owed)
    }

    @Test fun everySplitTypeStartsValidAfterSwitching() {
        val f = form()
        for (t in listOf(SplitType.PERCENT, SplitType.SHARES, SplitType.EXACT, SplitType.ADJUSTMENT, SplitType.EQUAL)) {
            f.switchSplit(t)
            assertNull(f.problem(), "after switching to $t")
            val input = f.toInput()
            assertEquals(10000L, input.shares.sumOf { it.owed }, "owed for $t")
            assertEquals(10000L, input.shares.sumOf { it.paid }, "paid for $t")
        }
    }

    @Test fun editRoundTripKeepsShares() {
        val e = Expense(
            id = 5, groupId = 7, description = "ארנונה", cost = 1001, currency = "ILS", date = "2026-09-01",
            splitType = "equal",
            shares = listOf(Share(1, 1001, 501), Share(2, 0, 500)),
        )
        val f = ExpenseForm(me = 1, defaultCurrency = "ILS")
        f.load(e)
        f.syncMembers(listOf(1, 2, 3)) // group has a third member who wasn't in the expense
        assertNull(f.problem())
        val out = f.toInput().shares.associate { it.userId to it.owed }
        assertEquals(mapOf(1 to 501L, 2 to 500L), out)
    }
}
