package dev.redfox.messages.core.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleOptionsTest {

    private val zone = ZoneId.of("Asia/Kolkata")
    private fun labels(now: LocalDateTime) = scheduleOptions(now, zone).map { it.label }

    @Test
    fun `morning offers later today, tonight and tomorrow`() = assertEquals(
        listOf("In 1 hour", "Later today, 6:00 pm", "Tonight, 9:00 pm", "Tomorrow, 9:00 am", "Tomorrow, 6:00 pm"),
        labels(LocalDateTime.of(2026, 10, 18, 10, 15)),
    )

    @Test
    fun `evening drops times that have passed or are under half an hour away`() = assertEquals(
        listOf("In 1 hour", "Tomorrow, 9:00 am", "Tomorrow, 6:00 pm"),
        labels(LocalDateTime.of(2026, 10, 18, 20, 45)),
    )

    @Test
    fun `in one hour is exactly an hour ahead, to the minute`() {
        val now = LocalDateTime.of(2026, 10, 18, 10, 15, 42)
        val expected = now.plusHours(1).withSecond(0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, scheduleOptions(now, zone).first().at)
    }
}
