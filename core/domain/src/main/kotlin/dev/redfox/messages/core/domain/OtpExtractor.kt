package dev.redfox.messages.core.domain

/**
 * Pulls the one-time code out of an OTP message ("482913 is your OTP…", "G-123456 is your
 * Google verification code"). Pure Kotlin so it's unit-tested.
 *
 * A message counts only when it has OTP wording *and* a 4–8 digit code. When there are
 * several numbers, the one nearest the wording wins, and numbers that are clearly something
 * else are skipped: amounts ("Rs.4999"), masked accounts ("XX1234"), and parts of a longer
 * number ("1,250.00").
 */
object OtpExtractor {

    private val keywords = Regex(
        """\b(otp|one[\s-]?time\s+(pass(word|code)?|pin)|verification\s+code|security\s+code|login\s+code|""" +
            """auth(entication)?\s+code|passcode|code\s+is|is\s+your\s+(\w+\s+)?code)\b""",
        RegexOption.IGNORE_CASE,
    )

    // A standalone 4–8 digit run: not part of an amount like "1,250.00" (separator *between* digits).
    private val digits = Regex("""(?<!\d)(?<!\d[.,])\d{4,8}(?!\d)(?![.,]\d)""")

    private val currencyBefore = Regex("""(rs\.?|inr|₹)\s?$""", RegexOption.IGNORE_CASE)

    fun extract(text: String): String? {
        val wording = keywords.findAll(text).map { it.range }.toList()
        if (wording.isEmpty()) return null
        return digits.findAll(text)
            .filterNot { isSomethingElse(text, it.range) }
            .minByOrNull { code -> wording.minOf { distance(code.range, it) } }
            ?.value
    }

    private fun isSomethingElse(text: String, range: IntRange): Boolean {
        val before = text.substring(maxOf(0, range.first - 5), range.first)
        // A letter glued to the front is a masked account or reference ("XX1234", "A1234").
        val glued = range.first > 0 && text[range.first - 1].isLetter()
        return glued || currencyBefore.containsMatchIn(before)
    }

    private fun distance(a: IntRange, b: IntRange): Int = when {
        a.last < b.first -> b.first - a.last
        b.last < a.first -> a.first - b.last
        else -> 0
    }
}
