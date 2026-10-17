package dev.redfox.messages.core.domain

/**
 * How a text splits into SMS segments, for the composer counter ("142 / 1"). Pure Kotlin so
 * it's unit-tested; mirrors the network rules rather than asking the radio:
 *  - text that fits the GSM 7-bit alphabet: 160 characters in one SMS, 153 per part when split.
 *    Characters from the extension table (`€ [ ] { } ~ ^ \ |`) take two slots;
 *  - anything else (emoji, Hindi, curly quotes…) switches the whole message to UCS-2:
 *    70 in one SMS, 67 per part. Emoji outside the BMP take two UTF-16 units each.
 */
data class SmsLength(
    val segments: Int,
    /** Characters left before another segment starts. */
    val remainingInSegment: Int,
    val isUnicode: Boolean,
) {
    companion object {
        private const val GSM_BASIC =
            "@£\$¥èéùìòÇ\nØø\rÅåΔ_ΦΓΛΩΠΨΣΘΞÆæßÉ !\"#¤%&'()*+,-./0123456789:;<=>?" +
                "¡ABCDEFGHIJKLMNOPQRSTUVWXYZÄÖÑÜ§¿abcdefghijklmnopqrstuvwxyzäöñüà"
        private const val GSM_EXTENDED = "^{}\\[~]|€\u000C"

        private const val GSM_SINGLE = 160
        private const val GSM_PART = 153
        private const val UCS_SINGLE = 70
        private const val UCS_PART = 67

        fun of(text: String): SmsLength {
            if (text.isEmpty()) return SmsLength(segments = 0, remainingInSegment = GSM_SINGLE, isUnicode = false)
            var septets = 0
            var unicode = false
            for (c in text) {
                when {
                    GSM_BASIC.indexOf(c) >= 0 -> septets += 1
                    GSM_EXTENDED.indexOf(c) >= 0 -> septets += 2
                    else -> { unicode = true; break }
                }
            }
            val units = if (unicode) text.length else septets // String.length counts UTF-16 units.
            val single = if (unicode) UCS_SINGLE else GSM_SINGLE
            val part = if (unicode) UCS_PART else GSM_PART
            return if (units <= single) {
                SmsLength(segments = 1, remainingInSegment = single - units, isUnicode = unicode)
            } else {
                val segments = (units + part - 1) / part
                SmsLength(segments = segments, remainingInSegment = segments * part - units, isUnicode = unicode)
            }
        }
    }
}
