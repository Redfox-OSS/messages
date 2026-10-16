package dev.redfox.messages.feature.thread

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.ObserveConversationUseCase
import dev.redfox.messages.core.domain.usecase.PagedMessagesUseCase
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.RichSuggestion
import dev.redfox.messages.core.model.SuggestionAction
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Immutable
data class ThreadState(
    val title: String = "",
    val participants: ImmutableList<Contact> = persistentListOf(),
) : UiState {
    val isGroup: Boolean get() = participants.size > 1
}

/** Read-only for now; the composer's intents arrive in M3. Paging is driven by the list itself. */
sealed interface ThreadIntent : UiIntent {
    data class SuggestionClicked(val suggestion: RichSuggestion) : ThreadIntent
}

sealed interface ThreadEffect : UiEffect {
    data class OpenUrl(val url: String) : ThreadEffect
    data class Dial(val phoneNumber: String) : ThreadEffect
}

/**
 * Conversation screen. Header state is MVI; the messages are a paged stream (newest first,
 * keyset-paged from the SMS store) with day dividers inserted as Paging separators.
 */
@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted threadId: Long,
    observeConversation: ObserveConversationUseCase,
    pagedMessages: PagedMessagesUseCase,
) : MviViewModel<ThreadState, ThreadIntent, ThreadEffect>(ThreadState()) {

    @AssistedFactory
    interface Factory {
        fun create(threadId: Long): ThreadViewModel
    }

    val timeFormat = TimeFormat()

    val rows: Flow<PagingData<ThreadRow>> = pagedMessages(threadId)
        .map { page ->
            val now = System.currentTimeMillis()
            page.map<_, ThreadRow> { ThreadRow.Msg(it) }
                .insertSeparators { newer, older ->
                    daySeparator((newer as? ThreadRow.Msg)?.message, (older as? ThreadRow.Msg)?.message, timeFormat, now)
                }
        }
        .cachedIn(viewModelScope)

    init {
        observeConversation(threadId).bind { conversation ->
            if (conversation == null) this
            else copy(title = conversation.title, participants = conversation.participants.toImmutableList())
        }
    }

    override suspend fun handle(intent: ThreadIntent) {
        when (intent) {
            is ThreadIntent.SuggestionClicked -> when (val action = intent.suggestion.action) {
                is SuggestionAction.OpenUrl -> emit(ThreadEffect.OpenUrl(action.url))
                is SuggestionAction.Dial -> emit(ThreadEffect.Dial(action.phoneNumber))
                SuggestionAction.Reply -> Unit // Needs RCS; reply buttons aren't shown.
            }
        }
    }
}
