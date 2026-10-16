package dev.redfox.messages.feature.conversations

import android.Manifest
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
import dev.redfox.messages.core.ui.components.ConversationRow
import dev.redfox.messages.core.ui.components.TabPage
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.obsidian.components.ChoicePill
import dev.redfox.obsidian.components.EmptyState
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
)

/** Rows beyond the first screenful skip the staggered entrance. */
private const val RevealedRows = 12

/** Reading the shared SMS store; contacts turn numbers into names and photos. */
private val AccessPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)

@Composable
fun ConversationsRoute(
    onOpenThread: (threadId: Long) -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val conversations = viewModel.conversations.collectAsLazyPagingItems()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.dispatch(ConversationsIntent.Refresh)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(ConversationsIntent.Refresh) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ConversationsEffect.OpenThread -> onOpenThread(effect.threadId)
            ConversationsEffect.RequestSmsAccess -> permissions.launch(AccessPermissions)
        }
    }
    ConversationsScreen(state = state, conversations = conversations, onIntent = viewModel::dispatch)
}

@Composable
fun ConversationsScreen(
    state: ConversationsState,
    conversations: LazyPagingItems<ConversationItem>,
    onIntent: (ConversationsIntent) -> Unit,
) {
    val settled = conversations.loadState.refresh is LoadState.NotLoading && !state.isIndexing
    TabPage(title = "Messages") { padding ->
        when {
            !state.hasSmsAccess -> AccessPrompt(padding, onIntent)
            else -> LazyColumn(
                modifier = Modifier.glassSource().fillMaxSize(),
                contentPadding = padding,
            ) {
                item(key = "filters", contentType = "filters") {
                    FilterRow(selected = state.filter, onIntent = onIntent)
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
                    ConversationRow(
                        conversation = item.conversation,
                        timeLabel = item.timeLabel,
                        onClick = { onIntent(ConversationsIntent.ConversationClicked(item.conversation.threadId)) },
                        modifier = Modifier
                            .animateItem(placementSpec = ObMotion.gentle())
                            // Only the first screenful plays the entrance; paged-in rows just appear.
                            .then(if (index < RevealedRows) Modifier.revealOnEnter(index + 1) else Modifier),
                    )
                }
            }
        }
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
