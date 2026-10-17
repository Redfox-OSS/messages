package dev.redfox.messages.feature.thread

import androidx.compose.runtime.Immutable
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import dev.redfox.messages.core.ui.mvi.cachedOffMain
import androidx.paging.insertSeparators
import androidx.paging.map
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.SmsLength
import dev.redfox.messages.core.domain.usecase.GetSimsUseCase
import dev.redfox.messages.core.domain.usecase.MarkThreadReadUseCase
import dev.redfox.messages.core.domain.usecase.ObserveConversationUseCase
import dev.redfox.messages.core.domain.usecase.PagedMessagesUseCase
import dev.redfox.messages.core.domain.usecase.RetryMessageUseCase
import dev.redfox.messages.core.domain.usecase.SendSmsUseCase
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.RichSuggestion
import dev.redfox.messages.core.model.SimCard
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
import kotlinx.coroutines.launch

@Immutable
data class ThreadState(
    val title: String = "",
    val participants: ImmutableList<Contact> = persistentListOf(),
    /** Who a reply goes to: the thread's participants, or the address a new chat was opened with. */
    val recipients: ImmutableList<String> = persistentListOf(),
    val draft: String = "",
    val sims: ImmutableList<SimCard> = persistentListOf(),
    val selectedSim: SimCard? = null,
    val isSending: Boolean = false,
) : UiState {
    val isGroup: Boolean get() = maxOf(participants.size, recipients.size) > 1

    /** Group conversations are MMS, which can't be sent yet; everything else replies by SMS. */
    val canReply: Boolean get() = recipients.size == 1 && acceptsReplies

    /** Business senders ("VM-HDFCBK", RCS chatbots) are send-only; only phone numbers can be texted back. */
    val acceptsReplies: Boolean get() = recipients.all { address -> address.none { it.isLetter() } }

    val canSend: Boolean get() = canReply && draft.isNotBlank() && !isSending

    val showSimPicker: Boolean get() = sims.size > 1

    /** "12 / 2" (characters left / segments) once the message is long enough to care. */
    val counter: String?
        get() {
            val length = SmsLength.of(draft)
            return if (length.segments > 1 || length.remainingInSegment <= COUNTER_THRESHOLD) {
                "${length.remainingInSegment} / ${length.segments}"
            } else null
        }

    private companion object {
        const val COUNTER_THRESHOLD = 20
    }
}

sealed interface ThreadIntent : UiIntent {
    data class SuggestionClicked(val suggestion: RichSuggestion) : ThreadIntent
    data class DraftChanged(val text: String) : ThreadIntent
    data object Send : ThreadIntent
    data object SwitchSim : ThreadIntent
    data class Retry(val message: Message) : ThreadIntent
}

sealed interface ThreadEffect : UiEffect {
    data class OpenUrl(val url: String) : ThreadEffect
    data class Dial(val phoneNumber: String) : ThreadEffect
    /** A message was just sent: bring the newest message into view. */
    data object ScrollToNewest : ThreadEffect
}

/**
 * Conversation screen. Header and composer state are MVI; the messages are a paged stream
 * (newest first, keyset-paged from the SMS store) with day dividers inserted as Paging
 * separators. Opening the thread marks it read, and so does anything arriving while it's open.
 */
@HiltViewModel(assistedFactory = ThreadViewModel.Factory::class)
class ThreadViewModel @AssistedInject constructor(
    @Assisted private val threadId: Long,
    @Assisted("addresses") addresses: List<String>,
    @Assisted("title") title: String?,
    @Assisted("draft") draft: String?,
    observeConversation: ObserveConversationUseCase,
    pagedMessages: PagedMessagesUseCase,
    private val getSims: GetSimsUseCase,
    private val sendSms: SendSmsUseCase,
    private val retryMessage: RetryMessageUseCase,
    private val markRead: MarkThreadReadUseCase,
) : MviViewModel<ThreadState, ThreadIntent, ThreadEffect>(
    ThreadState(
        title = title ?: addresses.joinToString(),
        recipients = addresses.toImmutableList(),
        draft = draft.orEmpty(),
    )
) {

    @AssistedFactory
    interface Factory {
        fun create(
            threadId: Long,
            @Assisted("addresses") addresses: List<String>,
            @Assisted("title") title: String?,
            @Assisted("draft") draft: String?,
        ): ThreadViewModel
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
        .cachedOffMain(this)

    init {
        viewModelScope.launch { markRead(threadId) }
        observeConversation(threadId).bind { conversation ->
            if (conversation == null) return@bind this
            // Something arrived while the conversation is open: it's been seen.
            if (conversation.isUnread) viewModelScope.launch { markRead(threadId) }
            copy(
                title = conversation.title,
                participants = conversation.participants.toImmutableList(),
                recipients = recipients.ifEmpty { conversation.participants.map { it.address }.toImmutableList() },
            )
        }
        viewModelScope.launch {
            val result = getSims()
            reduce {
                copy(
                    sims = result.sims.toImmutableList(),
                    selectedSim = result.sims.firstOrNull { it.subscriptionId == result.defaultSubscriptionId }
                        ?: result.sims.firstOrNull(),
                )
            }
        }
    }

    override suspend fun handle(intent: ThreadIntent) {
        when (intent) {
            is ThreadIntent.SuggestionClicked -> when (val action = intent.suggestion.action) {
                is SuggestionAction.OpenUrl -> emit(ThreadEffect.OpenUrl(action.url))
                is SuggestionAction.Dial -> emit(ThreadEffect.Dial(action.phoneNumber))
                SuggestionAction.Reply -> Unit // Needs RCS; reply buttons aren't shown.
            }
            is ThreadIntent.DraftChanged -> reduce { copy(draft = intent.text) }
            ThreadIntent.SwitchSim -> reduce {
                if (sims.size < 2) this
                else copy(selectedSim = sims[(sims.indexOf(selectedSim) + 1) % sims.size])
            }
            ThreadIntent.Send -> send()
            is ThreadIntent.Retry -> retryMessage(intent.message)
        }
    }

    private suspend fun send() {
        val snapshot = state.value
        if (!snapshot.canSend) return
        // Clear first so a double tap can't send twice; restore the text if nothing was sent.
        reduce { copy(draft = "", isSending = true) }
        val sent = runCatching {
            sendSms(snapshot.recipients, snapshot.draft, snapshot.selectedSim?.subscriptionId ?: NO_SIM)
        }.getOrDefault(false)
        reduce { copy(isSending = false, draft = if (sent) draft else snapshot.draft) }
        if (sent) emit(ThreadEffect.ScrollToNewest)
    }

    private companion object {
        /** Let the system pick its default SMS SIM. */
        const val NO_SIM = -1
    }
}
