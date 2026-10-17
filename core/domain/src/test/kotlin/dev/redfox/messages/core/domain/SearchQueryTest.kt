package dev.redfox.messages.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {

    private fun spans(query: String, text: String) =
        SearchQuery.parse(query).matches(text).map { text.substring(it.first, it.last + 1) }

    @Test
    fun `words become prefix phrases that all must match`() =
        assertEquals("\"your*\" \"otp*\"", SearchQuery.parse("  Your   OTP ").toMatch())

    @Test
    fun `numbers go to the digits column`() =
        assertEquals("numbers:4521*", SearchQuery.parse("4521").toMatch())

    @Test
    fun `thousands commas and currency signs are ignored in numbers`() {
        assertEquals("numbers:4500*", SearchQuery.parse("₹4,500").toMatch())
        assertEquals(listOf("4,500"), spans("4500", "Paid Rs 4,500 to Ravi"))
    }

    @Test
    fun `a hyphenated phone number is two numbers`() =
        assertEquals("numbers:98765* numbers:43210*", SearchQuery.parse("98765-43210").toMatch())

    @Test
    fun `punctuation in a word splits it into a phrase, never query syntax`() {
        assertEquals("\"a b*\"", SearchQuery.parse("a-b").toMatch())
        assertEquals("\"or*\" \"not*\"", SearchQuery.parse("OR NOT").toMatch())
        assertTrue(SearchQuery.parse("\"*()").isEmpty)
    }

    @Test
    fun `digits column holds every suffix of each digit run`() =
        assertEquals("4521 521 21 4500 500 00", SearchQuery.indexedNumbers("A/c XX4521 debited Rs 4,500"))

    @Test
    fun `words highlight at word starts only`() =
        assertEquals(listOf("Pay", "pay"), spans("pay", "Payment due. Repay now or pay later"))

    @Test
    fun `digits highlight anywhere in a run`() =
        assertEquals(listOf("4521"), spans("4521", "A/c XX4521 debited"))

    @Test
    fun `overlapping matches merge`() =
        assertEquals(listOf("payment"), spans("pay payment", "payment"))

    @Test
    fun `hindi words keep their vowel signs`() {
        val query = SearchQuery.parse("नमस्ते")
        assertEquals("\"नमस्ते*\"", query.toMatch())
        assertEquals(listOf("नमस्ते"), spans("नमस्ते", "नमस्ते दोस्त"))
    }
}
