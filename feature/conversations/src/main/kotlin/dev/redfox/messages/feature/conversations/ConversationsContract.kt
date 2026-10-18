package dev.redfox.messages.feature.conversations

import androidx.compose.runtime.Immutable
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf

@Immutable
data class ConversationItem(val conversation: Conversation, val timeLabel: String)

/**
 * Everything about the inbox except the rows themselves: those stream separately as
 * `PagingData` (a lazy, incrementally loaded stream doesn't belong in an immutable snapshot).
 */
@Immutable
data class ConversationsState(
    /** Optimistic until the first check, so a granted user never sees the permission prompt flash. */
    val hasSmsAccess: Boolean = true,
    val filter: InboxFilter = InboxFilter.All,
    /** True until the first index pass completes; an empty list before that means "loading". */
    val isIndexing: Boolean = true,
    /** 0–1 through the current sync pass. */
    val syncProgress: Float = 0f,
    val swipeLeft: SwipeAction = SwipeAction.Archive,
    val swipeRight: SwipeAction = SwipeAction.ToggleRead,
    /**
     * Conversations picked by long-press, by thread id. Not empty = selection mode: taps toggle rows,
     * swipes are off and the header shows the bulk actions.
     */
    val selected: PersistentMap<Long, Conversation> = persistentMapOf(),
    /** Conversations waiting for "Delete?" confirmation: one from a swipe, or the selection. */
    val confirmDelete: ImmutableList<Conversation> = persistentListOf(),
) : UiState {
    val isSelecting: Boolean get() = selected.isNotEmpty()

    /** The read action marks everything read while any selected conversation is unread, else unread. */
    val selectionMarksRead: Boolean get() = selected.values.any { it.isUnread }

    /** The archive action unarchives only when every selected conversation is archived. */
    val selectionUnarchives: Boolean get() = isSelecting && selected.values.all { it.isArchived }
}

/** This selection with [conversation] added, or removed if it was already in. */
internal fun PersistentMap<Long, Conversation>.toggled(conversation: Conversation): PersistentMap<Long, Conversation> =
    if (conversation.threadId in this) remove(conversation.threadId) else put(conversation.threadId, conversation)

sealed interface ConversationsIntent : UiIntent {
    /** Screen resumed: re-check access (the user may have changed it in system settings). */
    data object Refresh : ConversationsIntent
    data class FilterSelected(val filter: InboxFilter) : ConversationsIntent
    /** Opens the thread, or toggles the row while selecting. */
    data class ConversationClicked(val conversation: Conversation) : ConversationsIntent
    /** Starts selection mode with this row, or toggles it while selecting. */
    data class ConversationLongPressed(val conversation: Conversation) : ConversationsIntent
    data object ClearSelection : ConversationsIntent
    /** Marks the selection read, or unread when it's all read already ([ConversationsState.selectionMarksRead]). */
    data object ToggleReadSelected : ConversationsIntent
    /** Archives the selection, or unarchives it when it's all archived ([ConversationsState.selectionUnarchives]). */
    data object ArchiveSelected : ConversationsIntent
    /** Asks to confirm deleting the selection. */
    data object DeleteSelected : ConversationsIntent
    data object GrantAccessClicked : ConversationsIntent
    /** [left]: swiped from right to left. */
    data class Swiped(val conversation: Conversation, val left: Boolean) : ConversationsIntent
    data object ConfirmDelete : ConversationsIntent
    data object CancelDelete : ConversationsIntent
}

sealed interface ConversationsEffect : UiEffect {
    data class OpenThread(val threadId: Long) : ConversationsEffect
    data object RequestSmsAccess : ConversationsEffect
    data class Call(val phoneNumber: String) : ConversationsEffect
}
