package dev.redfox.messages.core.domain

/**
 * A search as typed, split into the pieces the full-text index understands. Words match at the
 * start of a word ("pay" finds "payment", not "repay"), case- and accent-insensitively; numbers
 * match anywhere inside a run of digits ("4521" finds "XX4521", "4500" finds "₹4,500").
 *
 * The same rules build the index query ([toMatch]) and find what to highlight ([matches]), so
 * what lights up is what was found.
 */
class SearchQuery private constructor(val terms: List<Term>) {

    /** One whitespace-separated piece of the query. */
    sealed interface Term {
        /** Word tokens, matched as a phrase; the last one may be a word start. */
        data class Words(val tokens: List<String>) : Term

        /** Two or more digits, matched anywhere in a digit run (thousands commas ignored). */
        data class Digits(val digits: String) : Term
    }

    val isEmpty: Boolean get() = terms.isEmpty()

    /**
     * The FTS4 MATCH expression: every term must match (implicit AND). Tokens are only letters,
     * digits and combining marks, so nothing the user types can be read as query syntax.
     */
    fun toMatch(): String = terms.joinToString(" ") { term ->
        when (term) {
            is Term.Digits -> "$NUMBERS_COLUMN:${term.digits}*"
            is Term.Words -> "\"" + term.tokens.joinToString(" ") + "*\""
        }
    }

    /** Where the terms occur in [text], sorted and non-overlapping, as [start, end) offsets. */
    fun matches(text: String): List<IntRange> {
        val found = ArrayList<IntRange>()
        for (term in terms) {
            when (term) {
                is Term.Digits -> found += digitMatches(text, term.digits)
                is Term.Words -> term.tokens.forEach { found += wordStartMatches(text, it) }
            }
        }
        if (found.isEmpty()) return found
        found.sortBy { it.first }
        val merged = ArrayList<IntRange>(found.size)
        for (range in found) {
            val last = merged.lastOrNull()
            if (last != null && range.first <= last.last) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }

    companion object {
        /** The index's digits column (see [indexedNumbers]). */
        const val NUMBERS_COLUMN = "numbers"

        /** Digit runs longer than this are indexed by their last [MAX_DIGIT_RUN] digits only. */
        private const val MAX_DIGIT_RUN = 20
        private const val MIN_DIGITS = 2

        fun parse(raw: String): SearchQuery {
            val terms = raw.trim().split(Whitespace).flatMap { piece ->
                val tokens = tokenize(removeThousandsCommas(piece))
                when {
                    tokens.isEmpty() -> emptyList()
                    // "4,500", "₹4500" or "98765-43210": each number matched anywhere in a digit run.
                    tokens.all { t -> t.all(Char::isDigit) } -> tokens.map { t ->
                        if (t.length >= MIN_DIGITS) Term.Digits(t.takeLast(MAX_DIGIT_RUN)) else Term.Words(listOf(t))
                    }
                    else -> listOf(Term.Words(tokens.map(String::lowercase)))
                }
            }
            return SearchQuery(terms.distinct())
        }

        /**
         * The digits column for one message: every suffix (2+ digits) of every digit run, so a
         * prefix query on it matches digits anywhere in the run. "XX4521" gives "4521 521 21".
         */
        fun indexedNumbers(text: String): String {
            val out = LinkedHashSet<String>()
            DigitRun.findAll(removeThousandsCommas(text)).forEach { match ->
                val run = match.value.takeLast(MAX_DIGIT_RUN)
                for (start in 0..run.length - MIN_DIGITS) out += run.substring(start)
            }
            return out.joinToString(" ")
        }

        /** Letters, digits and combining marks (Hindi and Bengali vowel signs are marks). */
        fun isTokenChar(c: Char): Boolean = c.isLetterOrDigit() || when (Character.getType(c).toByte()) {
            Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.ENCLOSING_MARK -> true
            else -> false
        }

        private fun tokenize(text: String): List<String> {
            val tokens = ArrayList<String>()
            val current = StringBuilder()
            for (c in text) {
                if (isTokenChar(c)) {
                    current.append(c)
                } else if (current.isNotEmpty()) {
                    tokens += current.toString()
                    current.clear()
                }
            }
            if (current.isNotEmpty()) tokens += current.toString()
            return tokens
        }

        private fun removeThousandsCommas(text: String) = ThousandsComma.replace(text, "")

        private fun wordStartMatches(text: String, token: String): List<IntRange> {
            val out = ArrayList<IntRange>()
            var from = 0
            while (true) {
                val at = text.indexOf(token, from, ignoreCase = true)
                if (at < 0) break
                if (at == 0 || !isTokenChar(text[at - 1])) out += at..<at + token.length
                from = at + 1
            }
            return out
        }

        /** [digits] anywhere in [text], stepping over thousands commas ("4,500" matches "4500"). */
        private fun digitMatches(text: String, digits: String): List<IntRange> {
            val out = ArrayList<IntRange>()
            var start = 0
            while (start < text.length) {
                if (text[start] == digits[0]) {
                    var i = start
                    var matched = 0
                    while (i < text.length && matched < digits.length) {
                        val c = text[i]
                        when {
                            c == digits[matched] -> matched++
                            c == ',' && matched > 0 && i + 1 < text.length && text[i - 1].isDigit() && text[i + 1].isDigit() -> Unit
                            else -> break
                        }
                        i++
                    }
                    if (matched == digits.length) {
                        out += start..<i
                        start = i
                        continue
                    }
                }
                start++
            }
            return out
        }

        private val Whitespace = Regex("""\s+""")
        private val DigitRun = Regex("""\d+""")
        private val ThousandsComma = Regex("""(?<=\d),(?=\d)""")
    }
}
