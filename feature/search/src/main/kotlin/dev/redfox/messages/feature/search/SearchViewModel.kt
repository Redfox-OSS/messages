package dev.redfox.messages.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.map
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.SearchQuery
import dev.redfox.messages.core.domain.usecase.SearchMessagesUseCase
import dev.redfox.messages.core.model.SearchHit
import dev.redfox.messages.core.model.SearchResults
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import dev.redfox.messages.core.ui.mvi.cachedOffMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SearchState(
    val query: String = "",
    /** People and the message count for [SearchResults.query], which may trail [query] while typing. */
    val results: SearchResults? = null,
    val isSearching: Boolean = false,
) : UiState {
    val tooShort: Boolean get() = query.trim().length < SearchMessagesUseCase.MIN_LENGTH
}

/** One matching message, ready to draw: the excerpt and what to highlight are worked out already. */
@Immutable
data class SearchRow(val hit: SearchHit, val excerpt: Excerpt, val timeLabel: String)

sealed interface SearchIntent : UiIntent {
    data class QueryChanged(val query: String) : SearchIntent
    /** The keyboard's search / enter key: search now, without waiting for the typing pause. */
    data object Submit : SearchIntent
    data object Clear : SearchIntent
    data object Closed : SearchIntent
}

sealed interface SearchEffect : UiEffect

/**
 * Search as you type (after a short pause, so each keystroke doesn't hit the index) and at once
 * on Enter. A newer query always wins: a slower, older search can't overwrite its results, and
 * its message pages stop loading.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMessages: SearchMessagesUseCase,
) : MviViewModel<SearchState, SearchIntent, SearchEffect>(SearchState()) {

    private var pending: Job? = null
    private val submitted = MutableStateFlow("")
    private val timeFormat = TimeFormat()

    /** Matching messages for the last query that ran, newest first, paged (built off the main thread). */
    val messages: Flow<PagingData<SearchRow>> = submitted
        .flatMapLatest { query ->
            val parsed = SearchQuery.parse(query)
            val now = System.currentTimeMillis()
            searchMessages.messages(query).map { page ->
                page.map { hit ->
                    SearchRow(hit, excerpt(hit.message.body, parsed), timeFormat.conversationTime(hit.message.timestamp, now))
                }
            }
        }
        .cachedOffMain(this)

    override suspend fun handle(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                reduce { copy(query = intent.query) }
                schedule(intent.query, TYPING_PAUSE_MILLIS)
            }
            SearchIntent.Submit -> schedule(state.value.query, 0)
            SearchIntent.Clear, SearchIntent.Closed -> {
                pending?.cancel()
                submitted.value = ""
                reduce { SearchState() }
            }
        }
    }

    private fun schedule(query: String, delayMillis: Long) {
        pending?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < SearchMessagesUseCase.MIN_LENGTH) {
            submitted.value = ""
            reduce { copy(results = null, isSearching = false) }
            return
        }
        pending = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            reduce { copy(isSearching = true) }
            submitted.value = trimmed
            val results = runCatching { searchMessages(trimmed) }.getOrNull()
            // Only the latest query lands.
            reduce { if (this.query.trim() == trimmed) copy(results = results, isSearching = false) else this }
        }
    }

    private companion object {
        const val TYPING_PAUSE_MILLIS = 200L
    }
}
