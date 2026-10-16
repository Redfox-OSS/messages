package dev.redfox.messages.core.ui.format

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/**
 * Message timestamps, Google Messages style. Pure functions of (time, now, zone) so ViewModels
 * can format once per emission and tests can pin the clock.
 */
class TimeFormat(
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val locale: Locale = Locale.getDefault(),
) {
    private val time = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    private val weekday = DateTimeFormatter.ofPattern("EEE", locale)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMM", locale)
    private val fullDate = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    private val dividerThisYear = DateTimeFormatter.ofPattern("EEE, d MMM", locale)

    /** Inbox: "10:42 AM" today, "Mon" this week, "13 Oct" this year, "13 Oct 2024" before. */
    fun conversationTime(epochMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
        val then = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val date = then.toLocalDate()
        return when {
            date == today -> then.format(time)
            ChronoUnit.DAYS.between(date, today) in 1..6 -> then.format(weekday)
            date.year == today.year -> then.format(dayMonth)
            else -> then.format(fullDate)
        }
    }

    /** Under a bubble: always the clock time; the day comes from the divider. */
    fun messageTime(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).atZone(zone).format(time)

    fun localDate(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()

    /** Day divider: "Today", "Yesterday", "Mon, 13 Oct", "13 Oct 2024". */
    fun dayLabel(date: LocalDate, nowMillis: Long = System.currentTimeMillis()): String {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return when {
            date == today -> "Today"
            date == today.minusDays(1) -> "Yesterday"
            date.year == today.year -> date.format(dividerThisYear)
            else -> date.format(fullDate)
        }
    }
}
