package dev.redfox.messages.feature.search

import dev.redfox.messages.core.domain.SearchQuery
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {

    private fun Excerpt.marked() = marks.map { text.substring(it.first, it.last + 1) }

    @Test
    fun `every match is marked, case-insensitively`() {
        val e = excerpt("Your OTP is 1234. Never share this otp.", SearchQuery.parse("otp"))
        assertEquals("Your OTP is 1234. Never share this otp.", e.text)
        assertEquals(listOf("OTP", "otp"), e.marked())
    }

    @Test
    fun `a match deep in a long message is shown with context`() {
        val long = "a".repeat(300) + " refund processed " + "b".repeat(100)
        val e = excerpt(long, SearchQuery.parse("refund"))
        assertTrue(e.text.startsWith("…"))
        assertTrue(e.text.contains("refund processed"))
        assertEquals(listOf("refund"), e.marked())
    }

    @Test
    fun `every word of the query is marked`() =
        assertEquals(listOf("Amaz", "4,500"), excerpt("Amazon order of Rs 4,500 shipped", SearchQuery.parse("4500 amaz")).marked())

    @Test
    fun `newlines become spaces so the excerpt stays on its lines`() =
        assertEquals("Hi there", excerpt("Hi\nthere", SearchQuery.parse("there")).text)
}
