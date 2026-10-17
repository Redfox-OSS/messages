package dev.redfox.messages.core.model

/** What a search found: conversations whose people match, and messages whose text matches. */
data class SearchResults(
    val query: String,
    val conversations: List<Conversation> = emptyList(),
    val messages: List<SearchHit> = emptyList(),
) {
    val isEmpty: Boolean get() = conversations.isEmpty() && messages.isEmpty()
}

/** A message whose text matched, with the conversation it belongs to (null if not indexed yet). */
data class SearchHit(val message: Message, val conversation: Conversation?)
