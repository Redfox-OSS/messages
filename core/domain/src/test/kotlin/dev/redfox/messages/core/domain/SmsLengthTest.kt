package dev.redfox.messages.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class SmsLengthTest {

    @Test
    fun `empty text is zero segments`() = assertEquals(SmsLength(0, 160, false), SmsLength.of(""))

    @Test
    fun `plain text fits one gsm segment`() = assertEquals(SmsLength(1, 155, false), SmsLength.of("Hello"))

    @Test
    fun `exactly 160 gsm characters is one segment`() =
        assertEquals(SmsLength(1, 0, false), SmsLength.of("a".repeat(160)))

    @Test
    fun `161 gsm characters splits into two 153-character parts`() =
        assertEquals(SmsLength(2, 145, false), SmsLength.of("a".repeat(161)))

    @Test
    fun `extension characters take two slots`() =
        assertEquals(SmsLength(1, 160 - 4, false), SmsLength.of("€{"))

    @Test
    fun `an emoji switches to unicode`() =
        assertEquals(SmsLength(1, 70 - 5, true), SmsLength.of("Hi 👍")) // 3 + emoji's 2 UTF-16 units

    @Test
    fun `long unicode text splits into 67-unit parts`() =
        assertEquals(SmsLength(2, 134 - 71, true), SmsLength.of("नमस्ते".repeat(11) + "न".repeat(5)))
}
