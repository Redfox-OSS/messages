package dev.redfox.messages.feature.search

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.SearchMessagesUseCase
import dev.redfox.messages.core.model.SearchResults
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

@Immutable
data class SearchState(
    val query: String = "",
    /** Results for [SearchResults.query], which may trail [query] while typing. */
    val results: SearchResults? = null,
    val isSearching: Boolean = false,
) : UiState {
    val tooShort: Boolean get() = query.trim().length < SearchMessagesUseCase.MIN_LENGTH
}

sealed interface SearchIntent : UiIntent {
    data class QueryChanged(val query: String) : SearchIntent
    /** The keyboard's search / enter key: search now, without waiting for the typing pause. */
    data object Submit : SearchIntent
    data object Clear : SearchIntent
    data object Closed : SearchIntent
}

sealed interface SearchEffect : UiEffect

/**
 * Search as you type (after a short pause, so each keystroke doesn't hit the store) and at once
 * on Enter. A newer query always wins: a slower, older search can't overwrite its results.
 */
@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchMessages: SearchMessagesUseCase,
) : MviViewModel<SearchState, SearchIntent, SearchEffect>(SearchState()) {

    private var pending: Job? = null

    override suspend fun handle(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.QueryChanged -> {
                reduce { copy(query = intent.query) }
                schedule(intent.query, TYPING_PAUSE_MILLIS)
            }
            SearchIntent.Submit -> schedule(state.value.query, 0)
            SearchIntent.Clear -> {
                pending?.cancel()
                reduce { SearchState() }
            }
            SearchIntent.Closed -> {
                pending?.cancel()
                reduce { SearchState() }
            }
        }
    }

    private fun schedule(query: String, delayMillis: Long) {
        pending?.cancel()
        val trimmed = query.trim()
        if (trimmed.length < SearchMessagesUseCase.MIN_LENGTH) {
            reduce { copy(results = null, isSearching = false) }
            return
        }
        pending = viewModelScope.launch {
            if (delayMillis > 0) delay(delayMillis)
            reduce { copy(isSearching = true) }
            val results = runCatching { searchMessages(trimmed) }.getOrNull()
            // Only the latest query lands.
            reduce { if (this.query.trim() == trimmed) copy(results = results, isSearching = false) else this }
        }
    }

    private companion object {
        const val TYPING_PAUSE_MILLIS = 250L
    }
}
