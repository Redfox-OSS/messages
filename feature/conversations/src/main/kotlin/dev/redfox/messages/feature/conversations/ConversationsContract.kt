package dev.redfox.messages.feature.conversations

import androidx.compose.runtime.Immutable
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState

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
    /** A conversation waiting for "Delete?" confirmation. */
    val confirmDelete: Conversation? = null,
) : UiState

sealed interface ConversationsIntent : UiIntent {
    /** Screen resumed: re-check access (the user may have changed it in system settings). */
    data object Refresh : ConversationsIntent
    data class FilterSelected(val filter: InboxFilter) : ConversationsIntent
    data class ConversationClicked(val threadId: Long) : ConversationsIntent
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
