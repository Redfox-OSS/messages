package dev.redfox.messages.core.ui.format

/** A link found in message text: where it is, and the URL to open. */
data class LinkMatch(val range: IntRange, val url: String)

/**
 * Finds web links in SMS text. Pure Kotlin (so it's unit-tested) and tuned for real Indian
 * SMS traffic:
 *  - anything with `http://` / `https://`, and `www.` addresses;
 *  - scheme-less short links that have a path, like `amzn.in/d/abc` or `bit.ly/xyz`.
 *
 * Bare words with a dot ("Rs.649", "A/c", "e.g.") never match: a scheme-less link needs a
 * known top-level domain *and* a path. Trailing punctuation that ends the sentence is not
 * part of the link.
 */
object Links {

    private const val TLDS =
        "com|in|co\\.in|org|net|io|me|app|ly|gl|co|info|gov\\.in|nic\\.in|org\\.in|net\\.in|edu|biz|xyz|" +
            "site|store|shop|online|link|page|to|cc|tv|ai|dev|club|live|today|bank|money|pay"

    private val pattern = Regex(
        """(?i)(?<![\w@/.])(""" +
            """(?:https?://|www\.)[^\s<>"]+""" +                          // explicit links
            """|(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+(?:$TLDS)/[^\s<>"]*""" + // short links with a path
            """)""",
    )

    /** Sentence punctuation that commonly follows a link but isn't part of it. */
    private const val TRAILING = ".,;:!?'\"*"

    fun find(text: String): List<LinkMatch> = pattern.findAll(text).mapNotNull { m ->
        var end = m.range.last
        val raw = m.value
        var value = raw
        // Trim trailing punctuation, and an unbalanced closing bracket ("(see example.com/x)").
        while (value.isNotEmpty()) {
            val last = value.last()
            val unbalancedClose = (last == ')' && value.count { it == '(' } < value.count { it == ')' }) ||
                (last == ']' && value.count { it == '[' } < value.count { it == ']' })
            if (last in TRAILING || unbalancedClose) {
                value = value.dropLast(1)
                end--
            } else break
        }
        if (value.length < 4 || !value.contains('.')) return@mapNotNull null
        LinkMatch(m.range.first..end, normalize(value))
    }.toList()

    /** Scheme-less links open as https; a scheme is lower-cased so the browser accepts it. */
    private fun normalize(link: String): String {
        val schemeEnd = link.indexOf("://")
        return if (schemeEnd > 0) link.substring(0, schemeEnd).lowercase() + link.substring(schemeEnd)
        else "https://$link"
    }
}
