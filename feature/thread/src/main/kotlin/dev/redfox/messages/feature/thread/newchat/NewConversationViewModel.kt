package dev.redfox.messages.feature.thread.newchat

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.ResolveThreadUseCase
import dev.redfox.messages.core.domain.usecase.SearchContactsUseCase
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import javax.inject.Inject

@Immutable
data class NewConversationState(
    val query: String = "",
    val results: ImmutableList<Contact> = persistentListOf(),
    val isOpening: Boolean = false,
) : UiState {
    /** What was typed, if it can be messaged directly: a phone number or short code. */
    val typedNumber: String?
        get() {
            val cleaned = query.filter { it.isDigit() || it == '+' }
            val looksLikeNumber = query.all { it.isDigit() || it in "+-() " } && cleaned.count { it.isDigit() } >= 3
            return cleaned.takeIf { looksLikeNumber }
        }
}

sealed interface NewConversationIntent : UiIntent {
    data class QueryChanged(val query: String) : NewConversationIntent
    data class Pick(val address: String, val title: String) : NewConversationIntent
}

sealed interface NewConversationEffect : UiEffect {
    data class OpenThread(val threadId: Long, val address: String, val title: String) : NewConversationEffect
}

/** Recipient picker: search contacts as you type, or message a typed number directly. */
@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewConversationViewModel @Inject constructor(
    private val searchContacts: SearchContactsUseCase,
    private val resolveThread: ResolveThreadUseCase,
) : MviViewModel<NewConversationState, NewConversationIntent, NewConversationEffect>(NewConversationState()) {

    private val queries = MutableStateFlow("")

    init {
        queries
            .debounce(QUERY_DEBOUNCE_MILLIS)
            .distinctUntilChanged()
            .mapLatest { searchContacts(it) }
            .bind { copy(results = it.toImmutableList()) }
    }

    override suspend fun handle(intent: NewConversationIntent) {
        when (intent) {
            is NewConversationIntent.QueryChanged -> {
                reduce { copy(query = intent.query) }
                queries.value = intent.query
            }
            is NewConversationIntent.Pick -> {
                if (state.value.isOpening) return
                reduce { copy(isOpening = true) }
                val threadId = runCatching { resolveThread(listOf(intent.address)) }.getOrNull()
                reduce { copy(isOpening = false) }
                if (threadId != null) emit(NewConversationEffect.OpenThread(threadId, intent.address, intent.title))
            }
        }
    }

    private companion object {
        const val QUERY_DEBOUNCE_MILLIS = 120L
    }
}
