package dev.redfox.messages.feature.search

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HighlightTest {

    @Test
    fun `every match is highlighted, case-insensitively`() {
        val text = highlight("Your OTP is 1234. Never share this otp.", "otp", Color.Red)
        assertEquals("Your OTP is 1234. Never share this otp.", text.text)
        assertEquals(listOf("OTP", "otp"), text.spanStyles.map { text.text.substring(it.start, it.end) })
    }

    @Test
    fun `a match deep in a long message is shown with context`() {
        val long = "a".repeat(300) + " refund processed " + "b".repeat(100)
        val text = highlight(long, "refund", Color.Red)
        assertTrue(text.text.startsWith("…"))
        assertTrue(text.text.contains("refund processed"))
        assertEquals(1, text.spanStyles.size)
    }

    @Test
    fun `newlines become spaces so the snippet stays on its lines`() =
        assertEquals("Hi there", highlight("Hi\nthere", "there", Color.Red).text)
}
