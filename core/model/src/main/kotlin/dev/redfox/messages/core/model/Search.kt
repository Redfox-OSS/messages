package dev.redfox.messages.core.model

/**
 * What a search found: conversations whose people match, and how many messages match (the
 * messages themselves load page by page). [messageCount] is null while the index is still
 * being built and the count isn't known.
 */
data class SearchResults(
    val query: String,
    val conversations: List<Conversation> = emptyList(),
    val messageCount: Int? = 0,
) {
    val isEmpty: Boolean get() = conversations.isEmpty() && messageCount == 0
}

/** A message whose text matched, with the conversation it belongs to (null if not indexed yet). */
data class SearchHit(val message: Message, val conversation: Conversation?)
