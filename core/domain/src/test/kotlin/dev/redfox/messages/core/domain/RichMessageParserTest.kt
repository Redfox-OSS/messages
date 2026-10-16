package dev.redfox.messages.core.domain

import dev.redfox.messages.core.model.MediaHeight
import dev.redfox.messages.core.model.SuggestionAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fixtures are synthetic, shaped like the GSMA botmessages Samsung stores. */
class RichMessageParserTest {

    private val card = """
        {
          "message": {
            "generalPurposeCard": {
              "layout": { "cardOrientation": "VERTICAL" },
              "content": {
                "title": "Weekend offer",
                "description": "Claim it before it's gone\n\nBestsellers:\nWraps",
                "media": {
                  "mediaUrl": "https://example.com/banner.jpg",
                  "mediaContentType": "image/jpeg",
                  "mediaFileSize": 255335,
                  "height": "MEDIUM_HEIGHT",
                  "dimensions": { "width": 2160, "height": 1080 }
                },
                "suggestions": [
                  { "action": { "displayText": "Order now", "postback": { "data": "{}" },
                      "urlAction": { "openUrl": { "url": "https://example.com/order", "application": "BROWSER" } } } },
                  { "action": { "displayText": "Call us", "postback": { "data": "{}" },
                      "dialerAction": { "dialPhoneNumber": { "phoneNumber": "+911234567890" } } } },
                  { "reply": { "displayText": "Not interested", "postback": { "data": "stop" } } }
                ]
              }
            }
          }
        }
    """.trimIndent()

    private val carousel = """
        { "message": { "generalPurposeCardCarousel": {
            "layout": { "cardWidth": "MEDIUM_WIDTH" },
            "content": [
              { "title": "One", "description": "First",
                "media": { "mediaUrl": "https://example.com/1.jpg", "height": "TALL_HEIGHT" } },
              { "description": "Second, untitled" },
              { "not": "a card" }
            ] } } }
    """.trimIndent()

    @Test
    fun `parses a single card with escapes decoded`() {
        val rich = RichMessageParser.parse(card)!!
        assertFalse(rich.isCarousel)
        val c = rich.cards.single()
        assertEquals("Weekend offer", c.title)
        assertEquals("Claim it before it's gone\n\nBestsellers:\nWraps", c.description)
        assertEquals("https://example.com/banner.jpg", c.media!!.url)
        assertEquals(MediaHeight.Medium, c.media!!.height)
        assertEquals(2f, c.media!!.aspectRatio!!, 0.001f)
    }

    @Test
    fun `maps every suggestion kind`() {
        val actions = RichMessageParser.parse(card)!!.cards.single().suggestions.map { it.label to it.action }
        assertEquals(
            listOf(
                "Order now" to SuggestionAction.OpenUrl("https://example.com/order"),
                "Call us" to SuggestionAction.Dial("+911234567890"),
                "Not interested" to SuggestionAction.Reply,
            ),
            actions,
        )
    }

    @Test
    fun `parses a carousel and skips empty cards`() {
        val rich = RichMessageParser.parse(carousel)!!
        assertTrue(rich.isCarousel)
        assertEquals(listOf("One", null), rich.cards.map { it.title })
        assertEquals(MediaHeight.Tall, rich.cards[0].media!!.height)
    }

    @Test
    fun `summary prefers title, then the first description line`() {
        assertEquals("Weekend offer", RichMessageParser.parse(card)!!.summary)
        val untitled = carousel.replace("\"title\": \"One\", ", "")
        assertEquals("First", RichMessageParser.parse(untitled)!!.summary)
    }

    @Test
    fun `padding blank lines are trimmed`() {
        val padded = card.replace("\"Weekend offer\"", "\"Weekend offer\\n\\n\"")
            .replace("\"Claim it", "\"\\n\\nClaim it")
        val c = RichMessageParser.parse(padded)!!.cards.single()
        assertEquals("Weekend offer", c.title)
        assertTrue(c.description!!.startsWith("Claim it"))
    }

    @Test
    fun `ordinary text is not rich`() {
        assertFalse(RichMessageParser.looksLikeRichMessage("Your OTP is 1234"))
        assertFalse(RichMessageParser.looksLikeRichMessage("{curly} but not a card"))
        assertNull(RichMessageParser.parse("{ \"message\": { \"text\": \"hi\" } }"))
        assertNull(RichMessageParser.parse("{ broken json"))
    }

    @Test
    fun `detects card json even with leading whitespace`() {
        assertTrue(RichMessageParser.looksLikeRichMessage("\n  $card"))
    }
}
