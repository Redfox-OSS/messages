package dev.redfox.messages.feature.thread

import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageBox
import dev.redfox.messages.core.model.MessageKind
import dev.redfox.messages.core.ui.components.BubblePosition
import dev.redfox.messages.core.ui.format.TimeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

class ThreadItemsTest {

    private val zone = ZoneOffset.UTC
    private val format = TimeFormat(zone, Locale.UK)
    private val now = millis(2026, 10, 17, 12, 0)

    private val alice = Contact("+911111111111", name = "Alice")
    private val bob = Contact("+912222222222", name = "Bob")

    private var nextId = 1L
    private fun msg(at: Long, from: Contact? = alice) = Message(
        id = nextId++, threadId = 1, kind = MessageKind.Sms,
        box = if (from == null) MessageBox.Sent else MessageBox.Inbox,
        sender = from, body = "m$nextId", timestamp = at, isRead = true, subscriptionId = 1,
    )

    private fun millis(y: Int, mo: Int, d: Int, h: Int, mi: Int) =
        LocalDateTime.of(y, mo, d, h, mi).toInstant(zone).toEpochMilli()

    /** Layouts for an oldest-first run, computed exactly as the paged list does from neighbours. */
    private fun layouts(vararg oldestFirst: Message, group: Boolean = false) = oldestFirst.indices.map { i ->
        bubbleLayout(oldestFirst.getOrNull(i - 1), oldestFirst[i], oldestFirst.getOrNull(i + 1), group, format)
    }

    @Test
    fun `a quick run from one side groups into first, middle, last`() {
        val t = millis(2026, 10, 17, 10, 0)
        val r = layouts(msg(t), msg(t + 30_000), msg(t + 60_000))
        assertEquals(listOf(BubblePosition.First, BubblePosition.Middle, BubblePosition.Last), r.map { it.position })
        assertNull(r[0].timeLabel)
        assertNull(r[1].timeLabel)
        assertNotNull(r[2].timeLabel)
    }

    @Test
    fun `switching sides or a long gap breaks the run`() {
        val t = millis(2026, 10, 17, 10, 0)
        val r = layouts(msg(t), msg(t + 10_000, from = null), msg(t + 3_600_000, from = null))
        assertEquals(List(3) { BubblePosition.Single }, r.map { it.position })
    }

    @Test
    fun `a run never crosses midnight`() {
        val r = layouts(msg(millis(2026, 10, 16, 23, 59)), msg(millis(2026, 10, 17, 0, 0)))
        assertEquals(List(2) { BubblePosition.Single }, r.map { it.position })
    }

    @Test
    fun `group threads label the first incoming bubble of each sender run`() {
        val t = millis(2026, 10, 17, 10, 0)
        val r = layouts(msg(t, alice), msg(t + 5_000, alice), msg(t + 10_000, bob), group = true)
        assertEquals(listOf(true, false, true), r.map { it.showSender })
    }

    @Test
    fun `no separator between messages of the same day`() {
        val t = millis(2026, 10, 17, 10, 0)
        assertNull(daySeparator(newer = msg(t + 1_000), older = msg(t), format = format, nowMillis = now))
    }

    @Test
    fun `separator carries the newer message's day when the day changes`() {
        val sep = daySeparator(msg(millis(2026, 10, 17, 9, 0)), msg(millis(2026, 10, 16, 9, 0)), format, now)
        assertEquals("Today", sep?.label)
    }

    @Test
    fun `the oldest message gets its day at the end of the thread`() {
        val sep = daySeparator(newer = msg(millis(2026, 10, 16, 9, 0)), older = null, format = format, nowMillis = now)
        assertEquals("Yesterday", sep?.label)
    }

    @Test
    fun `nothing before the newest row`() {
        assertNull(daySeparator(newer = null, older = msg(now), format = format, nowMillis = now))
    }
}
