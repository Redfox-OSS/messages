package dev.redfox.messages.core.domain.usecase

import dev.redfox.messages.core.domain.repository.SearchRepository
import dev.redfox.messages.core.model.SearchResults
import javax.inject.Inject

/** Searches people and message text. Queries shorter than [MIN_LENGTH] find nothing (too broad). */
class SearchMessagesUseCase @Inject constructor(
    private val search: SearchRepository,
) {
    suspend operator fun invoke(query: String): SearchResults {
        val trimmed = query.trim()
        if (trimmed.length < MIN_LENGTH) return SearchResults(trimmed)
        return search.search(trimmed, CONVERSATION_LIMIT, MESSAGE_LIMIT)
    }

    companion object {
        const val MIN_LENGTH = 2
        const val CONVERSATION_LIMIT = 8
        const val MESSAGE_LIMIT = 120
    }
}
