package dev.redfox.messages.feature.conversations

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.ui.R
import androidx.compose.foundation.background
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.ui.components.ConversationRow
import dev.redfox.obsidian.components.SwipeActionRow
import dev.redfox.obsidian.components.ObProgressBar
import dev.redfox.obsidian.components.SwipeVisual
import dev.redfox.messages.core.ui.platform.rememberCaller
import dev.redfox.obsidian.components.ObAlertDialog
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.components.TabPage
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.obsidian.components.ChoicePill
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.PrimaryButton
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.ObMotion
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.serialization.Serializable

@Serializable
data object ConversationsKey : NavKey

private val FilterLabels = mapOf(
    InboxFilter.All to "All",
    InboxFilter.Unread to "Unread",
    InboxFilter.Personal to "Personal",
    InboxFilter.Transactions to "Transactions",
    InboxFilter.Otp to "OTPs",
    InboxFilter.Offers to "Offers",
    InboxFilter.Archived to "Archived",
)

/** Rows beyond the first screenful skip the staggered entrance. */
private const val RevealedRows = 12

/** Reading the shared SMS store; contacts turn numbers into names and photos. */
private val AccessPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)

@Composable
fun ConversationsRoute(
    onOpenThread: (threadId: Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val conversations = viewModel.conversations.collectAsLazyPagingItems()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.dispatch(ConversationsIntent.Refresh)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(ConversationsIntent.Refresh) }
    val call = rememberCaller()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ConversationsEffect.Call -> call(effect.phoneNumber)
            is ConversationsEffect.OpenThread -> onOpenThread(effect.threadId)
            ConversationsEffect.RequestSmsAccess -> permissions.launch(AccessPermissions)
        }
    }
    ConversationsScreen(
        state = state,
        conversations = conversations,
        onOpenSettings = onOpenSettings,
        onIntent = viewModel::dispatch,
    )
}

@Composable
fun ConversationsScreen(
    state: ConversationsState,
    conversations: LazyPagingItems<ConversationItem>,
    onOpenSettings: () -> Unit,
    onIntent: (ConversationsIntent) -> Unit,
) {
    val settled = conversations.loadState.refresh is LoadState.NotLoading && !state.isIndexing
    BackHandler(enabled = state.isSelecting) { onIntent(ConversationsIntent.ClearSelection) }
    TabPage(
        title = if (state.isSelecting) "${state.selected.size} selected" else "Messages",
        actions = {
            if (state.isSelecting) SelectionActions(state, onIntent)
            else HeaderIconButton(R.drawable.ic_settings, contentDescription = "Settings", onClick = onOpenSettings)
        },
    ) { padding ->
        when {
            !state.hasSmsAccess -> AccessPrompt(padding, onIntent)
            else -> LazyColumn(
                modifier = Modifier.glassSource().fillMaxSize(),
                contentPadding = padding,
            ) {
                item(key = "filters", contentType = "filters") {
                    FilterRow(selected = state.filter, onIntent = onIntent)
                }
                // In the list, under the filters: it can't overlap them, and since it only shows
                // while the inbox is still empty (the first sync), nothing below it moves.
                item(key = "sync", contentType = "sync") {
                    ObProgressBar(
                        visible = state.isIndexing && conversations.itemCount == 0,
                        progress = state.syncProgress,
                        label = "Syncing messages",
                        modifier = Modifier.padding(top = ObSpacing.S),
                    )
                }
                if (settled && conversations.itemCount == 0) {
                    item(key = "empty", contentType = "empty") {
                        Box(Modifier.fillMaxWidth().height(420.dp)) {
                            EmptyState(
                                icon = R.drawable.ic_chat,
                                title = if (state.filter == InboxFilter.All) "No conversations yet" else "Nothing here",
                                subtitle = if (state.filter == InboxFilter.All) "Messages you send and receive appear here."
                                else "No ${FilterLabels.getValue(state.filter)} conversations.",
                            )
                        }
                    }
                }
                items(
                    count = conversations.itemCount,
                    key = conversations.itemKey { it.conversation.threadId },
                    contentType = conversations.itemContentType { "conversation" },
                ) { index ->
                    val item = conversations[index] ?: return@items
                    val conversation = item.conversation
                    SwipeActionRow(
                        // No swipes while selecting: a drag there is almost always a stray one.
                        left = if (state.isSelecting) null else swipeVisual(state.swipeLeft, conversation),
                        right = if (state.isSelecting) null else swipeVisual(state.swipeRight, conversation),
                        onSwipeLeft = { onIntent(ConversationsIntent.Swiped(conversation, left = true)) },
                        onSwipeRight = { onIntent(ConversationsIntent.Swiped(conversation, left = false)) },
                        modifier = Modifier
                            .animateItem(placementSpec = ObMotion.gentle())
                            // Only the first screenful plays the entrance; paged-in rows just appear.
                            .then(if (index < RevealedRows) Modifier.revealOnEnter(index + 1) else Modifier),
                    ) {
                        ConversationRow(
                            conversation = conversation,
                            timeLabel = item.timeLabel,
                            onClick = { onIntent(ConversationsIntent.ConversationClicked(conversation)) },
                            onLongClick = { onIntent(ConversationsIntent.ConversationLongPressed(conversation)) },
                            selected = conversation.threadId in state.selected,
                            // Opaque, so the swipe's colour only shows where the row has moved away.
                            modifier = Modifier.background(ObsidianTheme.colors.background),
                        )
                    }
                }
            }
        }
        if (state.confirmDelete.isNotEmpty()) {
            val single = state.confirmDelete.singleOrNull()
            ObAlertDialog(
                title = if (single != null) "Delete conversation?" else "Delete ${state.confirmDelete.size} conversations?",
                text = if (single != null) "All messages with ${single.title} are removed from this phone for good."
                else "All their messages are removed from this phone for good.",
                confirmLabel = "Delete",
                destructive = true,
                onConfirm = { onIntent(ConversationsIntent.ConfirmDelete) },
                onDismiss = { onIntent(ConversationsIntent.CancelDelete) },
            )
        }
    }
}

/** The header's bulk actions while selecting. Labels follow the selection, like the swipes do. */
@Composable
private fun SelectionActions(state: ConversationsState, onIntent: (ConversationsIntent) -> Unit) {
    HeaderIconButton(
        if (state.selectionMarksRead) R.drawable.ic_mark_chat_read else R.drawable.ic_mark_chat_unread,
        contentDescription = if (state.selectionMarksRead) "Mark read" else "Mark unread",
        onClick = { onIntent(ConversationsIntent.ToggleReadSelected) },
    )
    HeaderIconButton(
        if (state.selectionUnarchives) R.drawable.ic_unarchive else R.drawable.ic_archive,
        contentDescription = if (state.selectionUnarchives) "Unarchive" else "Archive",
        onClick = { onIntent(ConversationsIntent.ArchiveSelected) },
    )
    HeaderIconButton(
        R.drawable.ic_delete,
        contentDescription = "Delete",
        onClick = { onIntent(ConversationsIntent.DeleteSelected) },
    )
    HeaderIconButton(
        R.drawable.ic_close,
        contentDescription = "Cancel selection",
        onClick = { onIntent(ConversationsIntent.ClearSelection) },
    )
}

/** What a swipe reveals for this conversation; null when the action doesn't apply (e.g. calling a bank). */
@Composable
private fun swipeVisual(action: SwipeAction, conversation: Conversation): SwipeVisual? {
    val colors = ObsidianTheme.colors
    return when (action) {
        SwipeAction.None -> null
        SwipeAction.Archive ->
            if (conversation.isArchived) SwipeVisual("Unarchive", R.drawable.ic_unarchive, colors.accent)
            else SwipeVisual("Archive", R.drawable.ic_archive, colors.accent)
        SwipeAction.Delete -> SwipeVisual("Delete", R.drawable.ic_delete, colors.error)
        SwipeAction.ToggleRead ->
            if (conversation.isUnread) SwipeVisual("Mark read", R.drawable.ic_mark_chat_read, colors.accent)
            else SwipeVisual("Mark unread", R.drawable.ic_mark_chat_unread, colors.accent)
        SwipeAction.Pin ->
            SwipeVisual(if (conversation.isPinned) "Unpin" else "Pin", R.drawable.ic_keep, colors.accent)
        SwipeAction.Call ->
            callableNumber(conversation)?.let { SwipeVisual("Call", R.drawable.ic_call, colors.accent) }
    }
}

@Composable
private fun FilterRow(selected: InboxFilter, onIntent: (ConversationsIntent) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = ScreenGutter, vertical = ObSpacing.XS)
            .padding(bottom = ObSpacing.S),
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)
    ) {
        InboxFilter.entries.forEachIndexed { index, filter ->
            ChoicePill(
                text = FilterLabels.getValue(filter),
                selected = filter == selected,
                onClick = { onIntent(ConversationsIntent.FilterSelected(filter)) },
                modifier = Modifier.revealOnEnter(index),
            )
        }
    }
}

@Composable
private fun AccessPrompt(padding: PaddingValues, onIntent: (ConversationsIntent) -> Unit) {
    Column(
        Modifier.glassSource().fillMaxSize().padding(padding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.weight(1f)) {
            EmptyState(
                icon = R.drawable.ic_lock,
                title = "See your messages",
                subtitle = "Allow access to read the SMS and MMS already on this phone. Nothing leaves your device.",
            )
        }
        PrimaryButton(
            text = "Allow access",
            onClick = { onIntent(ConversationsIntent.GrantAccessClicked) },
            modifier = Modifier.padding(horizontal = ScreenGutter, vertical = ObSpacing.XL),
        )
    }
}
