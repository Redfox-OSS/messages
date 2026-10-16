package dev.redfox.messages.core.domain

import dev.redfox.messages.core.model.MediaHeight
import dev.redfox.messages.core.model.RichCard
import dev.redfox.messages.core.model.RichContent
import dev.redfox.messages.core.model.RichMedia
import dev.redfox.messages.core.model.RichSuggestion
import dev.redfox.messages.core.model.SuggestionAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull

/**
 * Parses GSMA RCS chatbot messages (`application/vnd.gsma.botmessage.v1.0+json`), the rich
 * cards brands send over RCS Business Messaging. Samsung Messages stores them in the shared
 * message store, either as a `botmessage` part or as a plain-text part holding the same JSON.
 *
 * Covers both shapes found in the wild: `message.generalPurposeCard` (one card) and
 * `message.generalPurposeCardCarousel` (several). Lenient: missing or unknown fields are
 * skipped, and anything that isn't a card returns null so it falls back to plain text.
 */
object RichMessageParser {

    const val CONTENT_TYPE_PREFIX = "application/vnd.gsma.botmessage"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    /** Cheap pre-check, so ordinary texts that happen to start with "{" aren't parsed. */
    fun looksLikeRichMessage(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("{") && trimmed.contains("generalPurposeCard")
    }

    fun parse(text: String): RichContent? {
        val root = runCatching { json.parseToJsonElement(text.trim()) }.getOrNull() as? JsonObject ?: return null
        val message = root.obj("message") ?: return null

        message.obj("generalPurposeCard")?.let { card ->
            val content = card.obj("content") ?: return null
            return parseCard(content)?.let { RichContent(listOf(it), isCarousel = false) }
        }
        message.obj("generalPurposeCardCarousel")?.let { carousel ->
            val cards = (carousel["content"] as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.let(::parseCard) }
            return cards.takeIf { it.isNotEmpty() }?.let { RichContent(it, isCarousel = true) }
        }
        return null
    }

    private fun parseCard(content: JsonObject): RichCard? {
        val card = RichCard(
            // Senders pad text with blank lines; trim so the card's own spacing is what shows.
            title = content.string("title")?.trim()?.takeIf { it.isNotEmpty() },
            description = content.string("description")?.trim()?.takeIf { it.isNotEmpty() },
            media = content.obj("media")?.let(::parseMedia),
            suggestions = (content["suggestions"] as? JsonArray).orEmpty().mapNotNull(::parseSuggestion),
        )
        return card.takeIf { it.title != null || it.description != null || it.media != null || it.suggestions.isNotEmpty() }
    }

    private fun parseMedia(media: JsonObject): RichMedia? {
        val url = media.string("mediaUrl")?.takeIf { it.isNotBlank() } ?: return null
        val dimensions = media.obj("dimensions")
        val width = dimensions?.float("width")
        val height = dimensions?.float("height")
        return RichMedia(
            url = url,
            contentType = media.string("mediaContentType"),
            thumbnailUrl = media.string("thumbnailUrl"),
            height = when (media.string("height")?.uppercase()) {
                "SHORT_HEIGHT" -> MediaHeight.Short
                "TALL_HEIGHT" -> MediaHeight.Tall
                else -> MediaHeight.Medium
            },
            aspectRatio = if (width != null && height != null && width > 0f && height > 0f) width / height else null,
        )
    }

    private fun parseSuggestion(element: JsonElement): RichSuggestion? {
        val suggestion = element as? JsonObject ?: return null
        suggestion.obj("reply")?.let { reply ->
            val label = reply.string("displayText") ?: return null
            return RichSuggestion(label, SuggestionAction.Reply)
        }
        val action = suggestion.obj("action") ?: return null
        val label = action.string("displayText") ?: return null
        action.obj("urlAction")?.obj("openUrl")?.string("url")?.let { url ->
            return RichSuggestion(label, SuggestionAction.OpenUrl(url))
        }
        action.obj("dialerAction")?.obj("dialPhoneNumber")?.string("phoneNumber")?.let { number ->
            return RichSuggestion(label, SuggestionAction.Dial(number))
        }
        // Other actions (calendar, maps, share location…) carry a postback only: treat as a reply.
        return RichSuggestion(label, SuggestionAction.Reply)
    }

    private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
    private fun JsonObject.float(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull
}
