package dev.redfox.messages.core.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class LinksTest {

    private fun urls(text: String) = Links.find(text).map { it.url }
    private fun spans(text: String) = Links.find(text).map { text.substring(it.range) }

    @Test
    fun `explicit https link`() = assertEquals(
        listOf("https://bsnlapp.bsnl.co.in/bsnl/app"),
        urls("Download: https://bsnlapp.bsnl.co.in/bsnl/app -BSNL"),
    )

    @Test
    fun `http and www links`() = assertEquals(
        listOf("http://example.com/a?b=1&c=2", "https://www.hdfcbank.com"),
        urls("Visit http://example.com/a?b=1&c=2 or www.hdfcbank.com today"),
    )

    @Test
    fun `scheme-less short links with a path`() = assertEquals(
        listOf("https://amzn.in/d/3xYz", "https://bit.ly/abc123", "https://i.hdfc.bank/x"),
        urls("Track: amzn.in/d/3xYz, offer bit.ly/abc123 and i.hdfc.bank/x"),
    )

    @Test
    fun `trailing sentence punctuation is not part of the link`() = assertEquals(
        listOf("https://example.com/x", "https://example.com/y"),
        spans("Go to https://example.com/x. Then https://example.com/y!").map { "$it" },
    )

    @Test
    fun `unbalanced closing bracket is trimmed, balanced is kept`() {
        assertEquals(listOf("https://example.com/x"), urls("(see https://example.com/x)"))
        assertEquals(listOf("https://en.wikipedia.org/wiki/A_(b)"), urls("https://en.wikipedia.org/wiki/A_(b)"))
    }

    @Test
    fun `amounts, accounts and abbreviations are not links`() = assertEquals(
        emptyList<String>(),
        urls("Rs.649 spent on A/c XX1234 via UPI. Avl Bal Rs.20,431.12 e.g. call 1503. Ref no.123"),
    )

    @Test
    fun `bare domain without a path is not linked`() = assertEquals(
        emptyList<String>(),
        urls("Contact us at support.example.com for help"),
    )

    @Test
    fun `emails are not treated as links`() = assertEquals(
        emptyList<String>(),
        urls("Write to care@example.com/x anytime"),
    )

    @Test
    fun `uppercase scheme is normalised`() = assertEquals(
        listOf("https://EXAMPLE.com/Path"),
        urls("HTTPS://EXAMPLE.com/Path"),
    )

    @Test
    fun `ranges point at the exact link text`() {
        val text = "Pay at https://pay.example.in/abc now"
        val match = Links.find(text).single()
        assertEquals("https://pay.example.in/abc", text.substring(match.range))
    }
}
