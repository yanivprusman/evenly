package com.automatelinux.evenly.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.automatelinux.evenly.data.model.Expense
import com.automatelinux.evenly.data.model.ExpenseInput
import com.automatelinux.evenly.util.SplitResult
import com.automatelinux.evenly.util.SplitType
import com.automatelinux.evenly.util.amountToInput
import com.automatelinux.evenly.util.buildShares
import com.automatelinux.evenly.util.computeOwed
import com.automatelinux.evenly.util.parseAmount
import com.automatelinux.evenly.util.suggestCategory
import com.automatelinux.evenly.util.today
import kotlinx.datetime.LocalDate

val REPEATS = listOf("none" to "Never", "weekly" to "Every week", "biweekly" to "Every 2 weeks", "monthly" to "Every month", "yearly" to "Every year")

/** All the state of the add/edit expense screen, plus the conversion to the wire format. */
class ExpenseForm(val me: Int, defaultCurrency: String) {
    var description by mutableStateOf("")
        private set
    var category by mutableStateOf("general")
    var categoryManual by mutableStateOf(false)
    var amountText by mutableStateOf("")
    var currency by mutableStateOf(defaultCurrency)
    var date by mutableStateOf(today())
    var notes by mutableStateOf("")
    var repeat by mutableStateOf("none")

    /** Exactly one of these defines who is in the expense. */
    var groupId by mutableStateOf<Int?>(null)
    val friendIds = mutableStateListOf<Int>()

    /** Everyone who can pay / owe, in display order (me first). Set by the screen from the context. */
    var members by mutableStateOf(listOf(me))
        private set

    var singlePayer by mutableStateOf<Int?>(me)   // null = multiple payers
    val paidText = mutableStateMapOf<Int, String>()

    var splitType by mutableStateOf(SplitType.EQUAL)
    val included = mutableStateListOf<Int>()
    val inputText = mutableStateMapOf<Int, String>()

    fun updateDescription(d: String) {
        description = d
        if (!categoryManual) suggestCategory(d)?.let { category = it } ?: run { if (d.isBlank()) category = "general" }
    }

    // After loading an existing expense, the first context sync must not pull group members who
    // weren't part of it into an equal split.
    private var justLoaded = false

    /** Called when the context changes. Keeps choices for people who are still there. */
    fun syncMembers(ids: List<Int>) {
        val ordered = (listOf(me) + ids.filter { it != me }).distinct()
        val keepSplit = justLoaded
        justLoaded = false
        if (ordered == members) return
        val added = ordered - members.toSet()
        members = ordered
        included.retainAll(ordered)
        if (!keepSplit) included.addAll(added)
        if (singlePayer != null && singlePayer !in ordered) singlePayer = me
        paidText.keys.retainAll(ordered.toSet())
        inputText.keys.retainAll(ordered.toSet())
    }

    val cost: Long? get() = parseAmount(amountText, currency)

    private fun parseSigned(t: String): Long? {
        val s = t.trim()
        if (s.isEmpty()) return 0
        val neg = s.startsWith("-")
        val v = parseAmount(s.removePrefix("-").removePrefix("+"), currency) ?: return null
        return if (neg) -v else v
    }

    /** Typed split values in the unit computeOwed expects; null entry = unparsable. */
    private fun inputs(): Map<Int, Double>? {
        val out = mutableMapOf<Int, Double>()
        for (id in members) {
            val t = inputText[id] ?: ""
            val v: Double = when (splitType) {
                SplitType.EQUAL -> 0.0
                SplitType.EXACT -> (if (t.isBlank()) 0L else parseAmount(t, currency) ?: return null).toDouble()
                SplitType.ADJUSTMENT -> (parseSigned(t) ?: return null).toDouble()
                SplitType.PERCENT, SplitType.SHARES -> if (t.isBlank()) 0.0 else t.trim().replace(',', '.').toDoubleOrNull() ?: return null
            }
            out[id] = v
        }
        return out
    }

    fun split(): SplitResult {
        val c = cost ?: 0L
        val ins = inputs() ?: return SplitResult(members.associateWith { 0L }, "Check the numbers you typed")
        return computeOwed(splitType, c, members, included.toSet(), ins)
    }

    /** Paid map + remainder (cost − Σpaid) for multi-payer mode. */
    fun paid(): Pair<Map<Int, Long>, Long>? {
        val c = cost ?: 0L
        val sp = singlePayer
        if (sp != null) return mapOf(sp to c) to 0L
        val m = mutableMapOf<Int, Long>()
        for (id in members) {
            val t = paidText[id] ?: ""
            m[id] = if (t.isBlank()) 0L else parseAmount(t, currency) ?: return null
        }
        return m to (c - m.values.sum())
    }

    /** First problem that blocks saving, or null when the form is valid. */
    fun problem(): String? {
        val c = cost
        if (description.isBlank()) return "Add a description"
        if (c == null || c <= 0) return "Enter an amount"
        if (members.size < 2) return "Choose who this is with"
        val p = paid() ?: return "Check the paid amounts"
        if (p.second != 0L) return "The payments must add up to the total"
        split().error?.let { return it }
        return null
    }

    fun toInput(): ExpenseInput {
        val c = cost ?: 0L
        val paid = paid()!!.first
        val owed = split().owed
        val ins = inputs() ?: emptyMap()
        val shares = buildShares(splitType, members, me, paid, owed, included.toSet(), ins)
        return ExpenseInput(
            groupId = groupId,
            description = description.trim(),
            cost = c,
            currency = currency,
            date = date.toString(),
            category = category,
            notes = notes.trim().ifBlank { null },
            isPayment = false,
            splitType = splitType.key,
            repeat = repeat,
            shares = shares,
        )
    }

    /** Fill the form from an existing expense (edit mode). */
    fun load(e: Expense) {
        description = e.description
        category = e.category
        categoryManual = true
        currency = e.currency
        amountText = amountToInput(e.cost, e.currency)
        date = runCatching { LocalDate.parse(e.date.take(10)) }.getOrDefault(today())
        notes = e.notes ?: ""
        repeat = e.repeat
        groupId = e.groupId
        friendIds.clear()
        if (e.groupId == null) friendIds.addAll(e.shares.map { it.userId }.filter { it != me })
        members = (listOf(me) + e.shares.map { it.userId }).distinct()
        val payers = e.shares.filter { it.paid > 0 }
        if (payers.size == 1 && payers[0].paid == e.cost) {
            singlePayer = payers[0].userId
        } else {
            singlePayer = null
            e.shares.forEach { if (it.paid > 0) paidText[it.userId] = amountToInput(it.paid, e.currency) }
        }
        splitType = SplitType.of(e.splitType)
        included.clear()
        inputText.clear()
        when (splitType) {
            SplitType.EQUAL -> included.addAll(e.shares.filter { it.owed > 0 }.map { it.userId })
            SplitType.ADJUSTMENT -> {
                included.addAll(e.shares.filter { it.input != null || it.owed > 0 }.map { it.userId })
                e.shares.forEach { s ->
                    val v = s.input?.toLong() ?: 0L
                    if (v != 0L) inputText[s.userId] = (if (v < 0) "-" else "") + amountToInput(kotlin.math.abs(v), e.currency)
                }
            }
            SplitType.EXACT -> {
                included.addAll(members)
                e.shares.forEach { s -> if (s.owed > 0) inputText[s.userId] = amountToInput(s.owed, e.currency) }
            }
            SplitType.PERCENT, SplitType.SHARES -> {
                included.addAll(members)
                e.shares.forEach { s ->
                    val v = s.input ?: 0.0
                    if (v != 0.0) inputText[s.userId] = if (v == kotlin.math.floor(v)) v.toLong().toString() else v.toString()
                }
            }
        }
        justLoaded = true
    }
}
