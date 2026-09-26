package com.automatelinux.evenly

import com.automatelinux.evenly.util.SplitType
import com.automatelinux.evenly.util.buildShares
import com.automatelinux.evenly.util.computeOwed
import com.automatelinux.evenly.util.formatAmount
import com.automatelinux.evenly.util.parseAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SplitMathTest {
    private val m = listOf(1, 2, 3)

    @Test fun equalGivesExtraAgoraToFirstMembers() {
        val r = computeOwed(SplitType.EQUAL, 1000, m, m.toSet(), emptyMap())
        assertNull(r.error)
        assertEquals(mapOf(1 to 334L, 2 to 333L, 3 to 333L), r.owed)
        assertEquals(1000L, r.owed.values.sum())
    }

    @Test fun equalRespectsExclusions() {
        val r = computeOwed(SplitType.EQUAL, 1001, m, setOf(2, 3), emptyMap())
        assertEquals(mapOf(1 to 0L, 2 to 501L, 3 to 500L), r.owed)
    }

    @Test fun exactReportsRemainder() {
        val r = computeOwed(SplitType.EXACT, 1000, m, emptySet(), mapOf(1 to 500.0, 2 to 300.0))
        assertNotNull(r.error)
        assertEquals(200L, r.remainder)
    }

    @Test fun percentThirds() {
        val r = computeOwed(SplitType.PERCENT, 10000, m, emptySet(), mapOf(1 to 33.34, 2 to 33.33, 3 to 33.33))
        assertNull(r.error)
        assertEquals(10000L, r.owed.values.sum())
        assertEquals(3334L, r.owed[1])
    }

    @Test fun percentMustReach100() {
        val r = computeOwed(SplitType.PERCENT, 10000, m, emptySet(), mapOf(1 to 50.0, 2 to 30.0))
        assertEquals(2000L, r.remainder)
    }

    @Test fun sharesWeighted() {
        val r = computeOwed(SplitType.SHARES, 1000, m, emptySet(), mapOf(1 to 2.0, 2 to 1.0, 3 to 0.0))
        assertEquals(mapOf(1 to 667L, 2 to 333L, 3 to 0L), r.owed)
    }

    @Test fun adjustmentAddsOnTopOfEqual() {
        val r = computeOwed(SplitType.ADJUSTMENT, 1000, m, m.toSet(), mapOf(1 to 100.0))
        assertNull(r.error)
        assertEquals(mapOf(1 to 400L, 2 to 300L, 3 to 300L), r.owed)
    }

    @Test fun sharesIncludeMeEvenWhenUninvolved() {
        val shares = buildShares(SplitType.EQUAL, listOf(1, 2, 3), me = 1,
            paid = mapOf(2 to 1000L), owed = mapOf(2 to 500L, 3 to 500L), included = setOf(2, 3), inputs = emptyMap())
        assertEquals(listOf(1, 2, 3), shares.map { it.userId })
        assertEquals(1000L, shares.sumOf { it.paid })
        assertEquals(1000L, shares.sumOf { it.owed })
    }

    @Test fun parsing() {
        assertEquals(19550L, parseAmount("195.5", "ILS"))
        assertEquals(123456L, parseAmount("1,234.56", "ILS"))
        assertNull(parseAmount("1.234", "ILS"))
        assertEquals("₪1,234.50", formatAmount(123450, "ILS", isolateBidi = false))
        assertEquals("₪0.05", formatAmount(-5, "ILS", isolateBidi = false))
    }
}
