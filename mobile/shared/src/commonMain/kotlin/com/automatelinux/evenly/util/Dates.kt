package com.automatelinux.evenly.util

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.todayIn

private val MONTHS = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)

fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

fun parseDate(s: String): LocalDate? = runCatching { LocalDate.parse(s.take(10)) }.getOrNull()

fun monthName(month: Int) = MONTHS[month - 1]
fun monthShort(month: Int) = MONTHS[month - 1].take(3)

/** "August 2026" */
fun monthHeader(date: String): String {
    val d = parseDate(date) ?: return date.take(7)
    return "${monthName(d.monthNumber)} ${d.year}"
}

/** "YYYY-MM" → "Aug 26" */
fun shortMonthLabel(yyyyMm: String): String {
    val parts = yyyyMm.split('-')
    val m = parts.getOrNull(1)?.toIntOrNull() ?: return yyyyMm
    return "${monthShort(m)} ${parts[0].takeLast(2)}"
}

/** "Sep 14, 2026" (year omitted when it is this year) */
fun prettyDate(date: String): String {
    val d = parseDate(date) ?: return date
    val base = "${monthShort(d.monthNumber)} ${d.dayOfMonth}"
    return if (d.year == today().year) base else "$base, ${d.year}"
}

fun LocalDate.toUtcMillis(): Long = atStartOfDayIn(TimeZone.UTC).toEpochMilliseconds()

fun utcMillisToDate(ms: Long): LocalDate =
    Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.UTC).date

fun parseInstant(s: String?): Instant? = s?.let { runCatching { Instant.parse(it) }.getOrNull() }

/** "just now", "5 min ago", "3 h ago", "yesterday", "4 days ago", "Aug 3" */
fun relativeTime(iso: String): String {
    val t = parseInstant(iso) ?: return iso.take(10)
    val secs = (Clock.System.now() - t).inWholeSeconds
    return when {
        secs < 60 -> "just now"
        secs < 3600 -> "${secs / 60} min ago"
        secs < 86400 -> "${secs / 3600} h ago"
        secs < 2 * 86400 -> "yesterday"
        secs < 7 * 86400 -> "${secs / 86400} days ago"
        else -> prettyDate(t.toLocalDateTime(TimeZone.currentSystemDefault()).date.toString())
    }
}
