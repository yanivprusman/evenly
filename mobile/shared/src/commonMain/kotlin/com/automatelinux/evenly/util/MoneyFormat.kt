package com.automatelinux.evenly.util

import com.automatelinux.evenly.data.model.Money
import kotlin.math.abs

private val SYMBOLS = mapOf(
    "ILS" to "₪", "USD" to "$", "EUR" to "€", "GBP" to "£", "JPY" to "¥", "CNY" to "¥",
    "INR" to "₹", "RUB" to "₽", "TRY" to "₺", "KRW" to "₩", "UAH" to "₴", "THB" to "฿",
    "PLN" to "zł", "CHF" to "CHF ", "CAD" to "CA$", "AUD" to "A$", "NZD" to "NZ$",
    "HUF" to "Ft ", "CZK" to "Kč ", "SEK" to "kr ", "NOK" to "kr ", "DKK" to "kr ",
    "BRL" to "R$", "MXN" to "MX$", "ZAR" to "R ", "AED" to "AED ", "EGP" to "E£", "JOD" to "JD ",
)

// ISO-4217 currencies whose minor unit is the unit itself.
private val ZERO_DECIMAL = setOf("JPY", "KRW", "CLP", "ISK", "VND")

/** Symbols learned from GET /api/currencies override the built-in table. */
object CurrencyTable {
    private val learned = mutableMapOf<String, String>()
    fun learn(code: String, symbol: String) { learned[code] = symbol }
    /** Letter symbols ("CHF", "lei") get a space so they don't run into the number. */
    fun symbol(code: String): String {
        val s = (learned[code] ?: SYMBOLS[code] ?: code).trimEnd()
        return if (s.last().isLetter()) "$s " else s
    }
    fun decimals(code: String): Int = if (code in ZERO_DECIMAL) 0 else 2
}

// Left-to-right isolate: keeps "₪1,234.50" glued together and on its own side when it sits
// inside a sentence next to Hebrew text.
const val LRI = "⁦"
const val FSI = "⁨"
const val PDI = "⁩"

/** Wraps user text (often Hebrew) so it cannot reorder the English sentence around it. */
fun isolate(text: String): String = FSI + text + PDI

private fun groupThousands(n: Long): String {
    val s = n.toString()
    val sb = StringBuilder()
    for ((i, c) in s.withIndex()) {
        if (i > 0 && (s.length - i) % 3 == 0) sb.append(',')
        sb.append(c)
    }
    return sb.toString()
}

/** Absolute value, formatted: ₪1,234.50. Signs are expressed in words by the caller. */
fun formatAmount(amount: Long, currency: String, isolateBidi: Boolean = true): String {
    val a = abs(amount)
    val dec = CurrencyTable.decimals(currency)
    val body = if (dec == 0) groupThousands(a) else {
        val whole = a / 100
        val frac = (a % 100).toString().padStart(2, '0')
        "${groupThousands(whole)}.$frac"
    }
    val text = CurrencyTable.symbol(currency) + body
    return if (isolateBidi) LRI + text + PDI else text
}

fun Money.format(isolateBidi: Boolean = true) = formatAmount(amount, currency, isolateBidi)

/** Parses a user-typed amount ("12", "12.5", "1,234.56") into minor units, or null. */
fun parseAmount(text: String, currency: String): Long? {
    val t = text.trim().replace(",", "")
    if (t.isEmpty()) return null
    val dec = CurrencyTable.decimals(currency)
    val parts = t.split('.')
    if (parts.size > 2) return null
    val whole = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    if (whole < 0) return null
    if (dec == 0) return if (parts.size == 1 || parts[1].all { it == '0' }) whole else null
    val fracStr = if (parts.size == 2) parts[1] else ""
    if (fracStr.length > 2 || !fracStr.all { it.isDigit() }) return null
    val frac = fracStr.padEnd(2, '0').toLong()
    return whole * 100 + frac
}

/** Minor units → plain editable text ("12.50"), no symbol, no grouping. */
fun amountToInput(amount: Long, currency: String): String {
    if (CurrencyTable.decimals(currency) == 0) return amount.toString()
    val frac = (amount % 100).toString().padStart(2, '0')
    return if (frac == "00") (amount / 100).toString() else "${amount / 100}.$frac"
}

/** Only non-zero entries; a list of zeros means "settled". */
fun List<Money>.nonZero() = filter { it.amount != 0L }

fun List<Money>.isSettled() = nonZero().isEmpty()
