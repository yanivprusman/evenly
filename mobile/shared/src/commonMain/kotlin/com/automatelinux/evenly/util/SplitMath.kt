package com.automatelinux.evenly.util

import com.automatelinux.evenly.data.model.Share
import kotlin.math.roundToLong

enum class SplitType(val key: String, val label: String) {
    EQUAL("equal", "Equally"),
    EXACT("exact", "Exact amounts"),
    PERCENT("percent", "Percentages"),
    SHARES("shares", "Shares"),
    ADJUSTMENT("adjustment", "Adjustment");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: EQUAL
    }
}

/**
 * Result of turning the split UI into owed amounts. [owed] is keyed by user id and, when
 * [error] is null, sums exactly to the cost. [remainder] is what is still unassigned
 * (positive = left to assign, negative = over-assigned), in the unit the UI shows
 * (minor units for exact/adjustment, basis points for percent, 0 otherwise).
 */
data class SplitResult(
    val owed: Map<Int, Long>,
    val error: String? = null,
    val remainder: Long = 0,
)

/** Splits [total] across [weights] (in order); the first members get the leftover units. */
fun distribute(total: Long, weights: List<Pair<Int, Long>>): Map<Int, Long> {
    val positive = weights.filter { it.second > 0 }
    val sum = positive.sumOf { it.second }
    if (sum == 0L || total == 0L) return weights.associate { it.first to 0L }
    val out = LinkedHashMap<Int, Long>()
    weights.forEach { out[it.first] = 0L }
    var assigned = 0L
    for ((id, w) in positive) {
        val v = total * w / sum
        out[id] = v
        assigned += v
    }
    var left = total - assigned
    var i = 0
    while (left > 0) {
        val id = positive[i % positive.size].first
        out[id] = out.getValue(id) + 1
        left--
        i++
    }
    return out
}

/** Percent typed by the user → basis points of a percent (33.33% → 3333). */
fun percentToBp(p: Double): Long = (p * 100).roundToLong()

/**
 * @param members every candidate member, in display order
 * @param included for EQUAL / ADJUSTMENT: who takes part
 * @param inputs per-member typed value: minor units (EXACT, ADJUSTMENT), percent (PERCENT),
 *               share count (SHARES). Missing = 0.
 */
fun computeOwed(
    type: SplitType,
    cost: Long,
    members: List<Int>,
    included: Set<Int>,
    inputs: Map<Int, Double>,
): SplitResult {
    val zero = members.associateWith { 0L }
    when (type) {
        SplitType.EQUAL -> {
            val inc = members.filter { it in included }
            if (inc.isEmpty()) return SplitResult(zero, "Pick at least one person")
            return SplitResult(zero + distribute(cost, inc.map { it to 1L }))
        }
        SplitType.EXACT -> {
            val owed = members.associateWith { (inputs[it] ?: 0.0).roundToLong() }
            if (owed.values.any { it < 0 }) return SplitResult(zero, "Amounts can't be negative")
            val rem = cost - owed.values.sum()
            return SplitResult(owed, if (rem != 0L) "Amounts must add up to the total" else null, rem)
        }
        SplitType.PERCENT -> {
            val bps = members.associateWith { percentToBp(inputs[it] ?: 0.0) }
            if (bps.values.any { it < 0 }) return SplitResult(zero, "Percentages can't be negative")
            val rem = 10000 - bps.values.sum()
            // Still show what each typed percentage is worth while the total is off.
            if (rem != 0L) return SplitResult(members.associateWith { cost * bps.getValue(it) / 10000 }, "Percentages must add up to 100%", rem)
            return SplitResult(zero + distribute(cost, members.map { it to bps.getValue(it) }))
        }
        SplitType.SHARES -> {
            // Shares may be fractional (1.5); scale to thousandths to keep integer math.
            val w = members.map { it to ((inputs[it] ?: 0.0) * 1000).roundToLong() }
            if (w.any { it.second < 0 }) return SplitResult(zero, "Shares can't be negative")
            if (w.sumOf { it.second } == 0L) return SplitResult(zero, "Give someone at least one share")
            return SplitResult(zero + distribute(cost, w))
        }
        SplitType.ADJUSTMENT -> {
            val inc = members.filter { it in included }
            if (inc.isEmpty()) return SplitResult(zero, "Pick at least one person")
            val adj = inc.associateWith { (inputs[it] ?: 0.0).roundToLong() }
            val rest = cost - adj.values.sum()
            if (rest < 0) return SplitResult(zero, "Adjustments are larger than the total", rest)
            val base = distribute(rest, inc.map { it to 1L })
            val owed = zero + inc.associateWith { base.getValue(it) + adj.getValue(it) }
            if (owed.values.any { it < 0 }) return SplitResult(zero, "Someone would owe a negative amount")
            return SplitResult(owed)
        }
    }
}

/**
 * Builds the wire shares. Every user who paid, owes, or is [me] appears once, [me] always
 * (the server requires the caller to be one of the shares).
 */
fun buildShares(
    type: SplitType,
    members: List<Int>,
    me: Int,
    paid: Map<Int, Long>,
    owed: Map<Int, Long>,
    included: Set<Int>,
    inputs: Map<Int, Double>,
): List<Share> {
    val ids = LinkedHashSet<Int>()
    members.forEach { id ->
        val p = paid[id] ?: 0L
        val o = owed[id] ?: 0L
        val takesPart = when (type) {
            SplitType.EQUAL, SplitType.ADJUSTMENT -> id in included
            else -> (inputs[id] ?: 0.0) != 0.0
        }
        if (p != 0L || o != 0L || takesPart || id == me) ids += id
    }
    ids += me
    return ids.map { id ->
        val input: Double? = when (type) {
            SplitType.EQUAL -> null
            SplitType.ADJUSTMENT -> if (id in included) inputs[id] ?: 0.0 else null
            else -> inputs[id] ?: 0.0
        }
        Share(userId = id, paid = paid[id] ?: 0L, owed = owed[id] ?: 0L, input = input)
    }
}
