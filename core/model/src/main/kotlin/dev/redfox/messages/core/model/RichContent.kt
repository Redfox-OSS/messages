package dev.redfox.messages.core.model

/**
 * A business chatbot message (GSMA RCS "botmessage": a card or a carousel of cards). Samsung
 * Messages stores these next to SMS/MMS; we render them as cards instead of raw JSON.
 */
data class RichContent(
    val cards: List<RichCard>,
    val isCarousel: Boolean,
) {
    /** One line for the inbox snippet and notifications. */
    val summary: String
        get() = cards.firstNotNullOfOrNull { card ->
            card.title?.takeIf { it.isNotBlank() } ?: card.description?.lineSequence()?.firstOrNull { it.isNotBlank() }
        } ?: "Rich message"
}

data class RichCard(
    val title: String?,
    val description: String?,
    val media: RichMedia?,
    val suggestions: List<RichSuggestion>,
)

data class RichMedia(
    val url: String,
    val contentType: String?,
    val thumbnailUrl: String?,
    val height: MediaHeight,
    /** width / height when the sender says, for a correctly sized placeholder. */
    val aspectRatio: Float?,
) {
    val isVideo: Boolean get() = contentType?.startsWith("video/") == true
}

/** The card's own size hint for its media. */
enum class MediaHeight { Short, Medium, Tall }

data class RichSuggestion(
    val label: String,
    val action: SuggestionAction,
)

sealed interface SuggestionAction {
    data class OpenUrl(val url: String) : SuggestionAction
    data class Dial(val phoneNumber: String) : SuggestionAction
    /** A canned reply to the chatbot. Needs RCS, which this app deliberately doesn't do. */
    data object Reply : SuggestionAction
}
