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
import dev.redfox.messages.core.domain.usecase.DeleteMessageUseCase
import dev.redfox.messages.core.domain.usecase.GetSimsUseCase
import dev.redfox.messages.core.domain.usecase.ManageScheduledUseCase
import dev.redfox.messages.core.domain.usecase.ObserveScheduledUseCase
import dev.redfox.messages.core.domain.usecase.ObserveStarredKeysUseCase
import dev.redfox.messages.core.domain.usecase.ScheduleMessageUseCase
import dev.redfox.messages.core.domain.usecase.SetStarredUseCase
import dev.redfox.messages.core.domain.usecase.MarkThreadReadUseCase
import dev.redfox.messages.core.domain.usecase.ObserveConversationUseCase
import dev.redfox.messages.core.domain.usecase.PagedMessagesUseCase
import dev.redfox.messages.core.domain.usecase.RetryMessageUseCase
import dev.redfox.messages.core.domain.usecase.SendSmsUseCase
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.RichSuggestion
import dev.redfox.messages.core.model.ScheduledMessage
import dev.redfox.messages.core.model.SimCard
import dev.redfox.messages.core.model.SuggestionAction
import dev.redfox.messages.core.ui.format.BrandLink
import dev.redfox.messages.core.ui.format.LinkBrands
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
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
    /** [Message.key]s starred in this thread. */
    val starredKeys: ImmutableSet<String> = persistentSetOf(),
    /** Messages scheduled in this thread, soonest first. */
    val scheduled: ImmutableList<ScheduledMessage> = persistentListOf(),
    /** The long-pressed message whose actions sheet is open. */
    val actionsFor: Message? = null,
    /** A message waiting for "Delete?" confirmation. */
    val confirmDelete: Message? = null,
    val showSchedulePicker: Boolean = false,
) : UiState {
    /** One phone number: the header gets a call button. */
    val callableNumber: String? get() = recipients.singleOrNull()?.takeIf { acceptsReplies }

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
    data class OpenBrandLink(val link: BrandLink) : ThreadIntent
    data class MessageLongPressed(val message: Message) : ThreadIntent
    data object DismissActions : ThreadIntent
    data object CopyMessage : ThreadIntent
    data object ToggleStar : ThreadIntent
    data object DeleteMessage : ThreadIntent
    data object ConfirmDelete : ThreadIntent
    data object CancelDelete : ThreadIntent
    /** Long-press on send: pick a time instead of sending now. */
    data object ScheduleClicked : ThreadIntent
    data object DismissSchedule : ThreadIntent
    data class ScheduleAt(val sendAt: Long) : ThreadIntent
    data class CancelScheduled(val id: Long) : ThreadIntent
    data class SendScheduledNow(val id: Long) : ThreadIntent
    data object CallClicked : ThreadIntent
}

sealed interface ThreadEffect : UiEffect {
    data class OpenUrl(val url: String) : ThreadEffect
    data class Dial(val phoneNumber: String) : ThreadEffect
    /** A message was just sent: bring the newest message into view. */
    data object ScrollToNewest : ThreadEffect
    data class CopyText(val text: String) : ThreadEffect
    data class Call(val phoneNumber: String) : ThreadEffect
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
    observeStarredKeys: ObserveStarredKeysUseCase,
    private val setStarred: SetStarredUseCase,
    private val deleteMessage: DeleteMessageUseCase,
    observeScheduled: ObserveScheduledUseCase,
    private val scheduleMessage: ScheduleMessageUseCase,
    private val manageScheduled: ManageScheduledUseCase,
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
            page.map<_, ThreadRow> { ThreadRow.Msg(it, LinkBrands.find(it.body)) }
                .insertSeparators { newer, older ->
                    daySeparator((newer as? ThreadRow.Msg)?.message, (older as? ThreadRow.Msg)?.message, timeFormat, now)
                }
        }
        .cachedOffMain(this)

    init {
        viewModelScope.launch { markRead(threadId) }
        observeStarredKeys(threadId).bind { copy(starredKeys = it.toImmutableSet()) }
        observeScheduled.forThread(threadId).bind { copy(scheduled = it.toImmutableList()) }
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
            is ThreadIntent.MessageLongPressed -> reduce { copy(actionsFor = intent.message) }
            ThreadIntent.DismissActions -> reduce { copy(actionsFor = null) }
            ThreadIntent.CopyMessage -> {
                val message = state.value.actionsFor ?: return
                reduce { copy(actionsFor = null) }
                emit(ThreadEffect.CopyText(message.body))
            }
            ThreadIntent.ToggleStar -> {
                val snapshot = state.value
                val message = snapshot.actionsFor ?: return
                reduce { copy(actionsFor = null) }
                setStarred(message, snapshot.title, starred = message.key !in snapshot.starredKeys)
            }
            ThreadIntent.DeleteMessage -> reduce { copy(confirmDelete = actionsFor, actionsFor = null) }
            ThreadIntent.CancelDelete -> reduce { copy(confirmDelete = null) }
            ThreadIntent.ConfirmDelete -> {
                val message = state.value.confirmDelete ?: return
                reduce { copy(confirmDelete = null) }
                runCatching { deleteMessage(message) }
            }
            ThreadIntent.ScheduleClicked -> if (state.value.canSend) reduce { copy(showSchedulePicker = true) }
            ThreadIntent.DismissSchedule -> reduce { copy(showSchedulePicker = false) }
            is ThreadIntent.ScheduleAt -> schedule(intent.sendAt)
            is ThreadIntent.CancelScheduled -> manageScheduled.cancel(intent.id)
            is ThreadIntent.SendScheduledNow -> {
                manageScheduled.sendNow(intent.id)
                emit(ThreadEffect.ScrollToNewest)
            }
            ThreadIntent.CallClicked -> state.value.callableNumber?.let { emit(ThreadEffect.Call(it)) }
            // Same as tapping the link itself: Android opens it in the app that claims it, else the browser.
            is ThreadIntent.OpenBrandLink -> emit(ThreadEffect.OpenUrl(intent.link.url))
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

    private suspend fun schedule(sendAt: Long) {
        val snapshot = state.value
        reduce { copy(showSchedulePicker = false) }
        val scheduled = scheduleMessage(
            threadId = threadId,
            recipients = snapshot.recipients,
            title = snapshot.title,
            body = snapshot.draft,
            subscriptionId = snapshot.selectedSim?.subscriptionId ?: NO_SIM,
            sendAt = sendAt,
        )
        if (scheduled) reduce { copy(draft = "") }
    }

    private companion object {
        /** Let the system pick its default SMS SIM. */
        const val NO_SIM = -1
    }
}
