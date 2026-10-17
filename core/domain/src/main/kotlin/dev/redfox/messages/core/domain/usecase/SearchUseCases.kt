package dev.redfox.messages.core.domain.usecase

import androidx.paging.PagingData
import dev.redfox.messages.core.domain.repository.SearchRepository
import dev.redfox.messages.core.model.SearchHit
import dev.redfox.messages.core.model.SearchResults
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject

/** Searches people, and counts matching messages. Queries shorter than [MIN_LENGTH] find nothing (too broad). */
class SearchMessagesUseCase @Inject constructor(
    private val search: SearchRepository,
) {
    suspend operator fun invoke(query: String): SearchResults {
        val trimmed = query.trim()
        if (trimmed.length < MIN_LENGTH) return SearchResults(trimmed)
        return search.search(trimmed, CONVERSATION_LIMIT)
    }

    /** The matching messages themselves, newest first, a page at a time. */
    fun messages(query: String): Flow<PagingData<SearchHit>> {
        val trimmed = query.trim()
        return if (trimmed.length < MIN_LENGTH) flowOf(PagingData.empty()) else search.messages(trimmed)
    }

    companion object {
        const val MIN_LENGTH = 2
        const val CONVERSATION_LIMIT = 8
    }
}
