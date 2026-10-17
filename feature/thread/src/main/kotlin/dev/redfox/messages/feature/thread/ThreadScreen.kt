package dev.redfox.messages.feature.thread

import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import dev.redfox.messages.core.model.ScheduledMessage
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.ObSheet
import dev.redfox.messages.core.ui.components.SchedulePicker
import dev.redfox.messages.core.ui.components.SheetAction
import dev.redfox.messages.core.ui.components.formatScheduleTime
import dev.redfox.messages.core.ui.platform.rememberCaller
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.ObAlertDialog
import dev.redfox.obsidian.effects.glassEffect
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.redfox.messages.core.ui.components.BubblePosition
import dev.redfox.messages.core.ui.components.ComposerBar
import dev.redfox.messages.core.ui.components.ComposerNotice
import dev.redfox.messages.core.ui.components.DateDivider
import dev.redfox.messages.core.ui.components.DetailPage
import dev.redfox.messages.core.ui.components.MessageBubble
import dev.redfox.messages.core.ui.components.RichCardBubble
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import kotlinx.serialization.Serializable

/**
 * A conversation. [addresses] and [title] are set when it's opened for someone new (from the
 * recipient picker or another app's "send SMS" link), before the thread has any messages;
 * [draft] pre-fills the composer.
 */
@Serializable
data class ThreadKey(
    val threadId: Long,
    val addresses: List<String> = emptyList(),
    val title: String? = null,
    val draft: String? = null,
) : NavKey

/** Only the newest screenful plays the entrance; older history just appears. */
private const val RevealedRows = 12

@Composable
fun ThreadRoute(
    key: ThreadKey,
    onBack: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(
        creationCallback = { factory -> factory.create(key.threadId, key.addresses, key.title, key.draft) }
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    var scrollToSent by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val call = rememberCaller()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ThreadEffect.OpenUrl -> runCatching { uriHandler.openUri(effect.url) }
            is ThreadEffect.Dial -> runCatching {
                // ACTION_DIAL only pre-fills the dialler: no call permission, the user confirms.
                context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${effect.phoneNumber}".toUri()))
            }
            // The sent message isn't in the list yet; FollowNewest scrolls once it lands.
            ThreadEffect.ScrollToNewest -> scrollToSent = true
            is ThreadEffect.CopyText -> context.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("Message", effect.text))
            is ThreadEffect.Call -> call(effect.phoneNumber)
        }
    }
    FollowNewest(rows, listState, scrollToSent = { scrollToSent }, onScrolled = { scrollToSent = false })
    ThreadScreen(
        state = state,
        rows = rows,
        listState = listState,
        timeFormat = viewModel.timeFormat,
        onBack = onBack,
        onIntent = viewModel::dispatch,
    )
}

@Composable
fun ThreadScreen(
    state: ThreadState,
    rows: LazyPagingItems<ThreadRow>,
    listState: LazyListState,
    timeFormat: TimeFormat,
    onBack: () -> Unit,
    onIntent: (ThreadIntent) -> Unit,
) {
    val density = LocalDensity.current
    // The composer floats over the list; the list's end padding follows its real height.
    var composerHeight by remember { mutableIntStateOf(0) }

    DetailPage(
        title = state.title,
        onBack = onBack,
        actions = {
            if (state.callableNumber != null) {
                HeaderIconButton(R.drawable.ic_call, contentDescription = "Call", onClick = { onIntent(ThreadIntent.CallClicked) })
            }
        },
    ) { padding ->
        // The keyboard pushes list and composer up together.
        Box(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.glassSource().fillMaxSize(),
                // Reversed: "top" padding sits under the header, "bottom" above the composer.
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = with(density) { composerHeight.toDp() } + ObSpacing.S,
                ),
            ) {
                items(
                    count = rows.itemCount,
                    key = rows.itemKey { it.key },
                    contentType = rows.itemContentType { it::class },
                ) { index ->
                    val row = rows[index] ?: return@items
                    val reveal = if (index < RevealedRows) Modifier.revealOnEnter(index, offsetY = 12.dp) else Modifier
                    when (row) {
                        is ThreadRow.Day -> DateDivider(row.label, reveal)
                        is ThreadRow.Msg -> {
                            // Neighbours via peek(): reads what's loaded without triggering more paging.
                            // reverseLayout: index + 1 is the older row (above), index - 1 the newer (below).
                            val layout = bubbleLayout(
                                older = (rows.peekOrNull(index + 1) as? ThreadRow.Msg)?.message,
                                message = row.message,
                                newer = (rows.peekOrNull(index - 1) as? ThreadRow.Msg)?.message,
                                isGroup = state.isGroup,
                                format = timeFormat,
                            )
                            val rich = row.message.rich
                            if (rich != null) RichCardBubble(
                                rich = rich,
                                timeLabel = layout.timeLabel,
                                onSuggestion = { onIntent(ThreadIntent.SuggestionClicked(it)) },
                                modifier = reveal.padding(top = ObSpacing.S),
                            ) else MessageBubble(
                                message = row.message,
                                position = layout.position,
                                timeLabel = layout.timeLabel,
                                showSenderName = layout.showSender,
                                onRetry = { onIntent(ThreadIntent.Retry(row.message)) },
                                brandLinks = row.brandLinks,
                                onOpenBrandLink = { onIntent(ThreadIntent.OpenBrandLink(it)) },
                                isStarred = row.message.key in state.starredKeys,
                                onLongPress = { onIntent(ThreadIntent.MessageLongPressed(row.message)) },
                                modifier = reveal.padding(
                                    // Tight gap inside a run, a breath between runs.
                                    top = if (layout.position == BubblePosition.Middle || layout.position == BubblePosition.Last) 2.dp
                                    else ObSpacing.S,
                                ),
                            )
                        }
                    }
                }
            }

            val composerModifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { composerHeight = it.height }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = ObSpacing.S)
            if (state.canReply) Column(composerModifier, verticalArrangement = Arrangement.spacedBy(ObSpacing.XS)) {
                state.scheduled.forEach { scheduled ->
                    ScheduledStrip(
                        scheduled = scheduled,
                        onSendNow = { onIntent(ThreadIntent.SendScheduledNow(scheduled.id)) },
                        onCancel = { onIntent(ThreadIntent.CancelScheduled(scheduled.id)) },
                    )
                }
                ComposerBar(
                    text = state.draft,
                    onTextChange = { onIntent(ThreadIntent.DraftChanged(it)) },
                    onSend = { onIntent(ThreadIntent.Send) },
                    canSend = state.canSend,
                    sim = state.selectedSim,
                    showSim = state.showSimPicker,
                    onSimClick = { onIntent(ThreadIntent.SwitchSim) },
                    counter = state.counter,
                    onSendLongPress = { onIntent(ThreadIntent.ScheduleClicked) },
                )
            } else if (state.recipients.isNotEmpty()) {
                ComposerNotice(
                    if (!state.acceptsReplies) "This sender doesn't accept replies"
                    else "Group replies need MMS, which isn't supported yet",
                    composerModifier,
                )
            }
        }
    }

    state.actionsFor?.let { message ->
        ObSheet(onDismiss = { onIntent(ThreadIntent.DismissActions) }) {
            if (message.body.isNotEmpty()) {
                SheetAction("Copy text", R.drawable.ic_content_copy, onClick = { onIntent(ThreadIntent.CopyMessage) })
            }
            val starred = message.key in state.starredKeys
            SheetAction(
                if (starred) "Unstar" else "Star",
                if (starred) R.drawable.ic_star_filled else R.drawable.ic_star,
                onClick = { onIntent(ThreadIntent.ToggleStar) },
            )
            SheetAction("Delete", R.drawable.ic_delete, destructive = true, onClick = { onIntent(ThreadIntent.DeleteMessage) })
        }
    }
    if (state.confirmDelete != null) {
        ObAlertDialog(
            title = "Delete this message?",
            text = "It's removed from this phone for good.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = { onIntent(ThreadIntent.ConfirmDelete) },
            onDismiss = { onIntent(ThreadIntent.CancelDelete) },
        )
    }
    if (state.showSchedulePicker) {
        SchedulePicker(onPick = { onIntent(ThreadIntent.ScheduleAt(it)) }, onDismiss = { onIntent(ThreadIntent.DismissSchedule) })
    }
}

/** A scheduled message waiting above the composer: when, a preview, send now or cancel. */
@Composable
private fun ScheduledStrip(scheduled: ScheduledMessage, onSendNow: () -> Unit, onCancel: () -> Unit) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter - ObSpacing.XXS)
            .clip(RoundedCornerShape(20.dp))
            .glassEffect(colors.navScrim)
            .border(1.dp, if (scheduled.failed) colors.error else colors.accentBorder, RoundedCornerShape(20.dp))
            .padding(start = ObSpacing.L, end = ObSpacing.XS, top = ObSpacing.XS, bottom = ObSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.S),
    ) {
        Icon(painterResource(R.drawable.ic_schedule_send), contentDescription = null, tint = colors.accentLight, modifier = Modifier.size(18.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (scheduled.failed) "Couldn't send · tap Send now to retry" else formatScheduleTime(scheduled.sendAt),
                style = type.labelStrong,
                color = if (scheduled.failed) colors.error else colors.accentLight,
            )
            Text(scheduled.body, style = type.label, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        HeaderIconButton(R.drawable.ic_send, contentDescription = "Send now", onClick = onSendNow, tint = colors.textSecondary)
        HeaderIconButton(R.drawable.ic_close, contentDescription = "Cancel scheduled message", onClick = onCancel, tint = colors.textSecondary)
    }
}

/**
 * Keeps the newest message in view when one arrives. A new message is inserted *before* the
 * first visible item, and LazyColumn holds that item still, so without this the new bubble
 * lands just below the screen. Follows when the user just sent something, or was already at
 * the bottom; someone reading older history isn't yanked down.
 */
@Composable
private fun FollowNewest(
    rows: LazyPagingItems<ThreadRow>,
    listState: LazyListState,
    scrollToSent: () -> Boolean,
    onScrolled: () -> Unit,
) {
    val currentScrollToSent by rememberUpdatedState(scrollToSent)
    val currentOnScrolled by rememberUpdatedState(onScrolled)
    LaunchedEffect(rows, listState) {
        var newest: String? = null
        snapshotFlow { if (rows.itemCount > 0) rows.peek(0)?.key else null }
            .collect { key ->
                val arrived = key != null && key != newest
                val atBottom = listState.firstVisibleItemIndex <= 1
                if (arrived && (currentScrollToSent() || (newest != null && atBottom))) {
                    listState.animateScrollToItem(0)
                    currentOnScrolled()
                }
                newest = key
            }
    }
}

private fun <T : Any> LazyPagingItems<T>.peekOrNull(index: Int): T? =
    if (index in 0 until itemCount) peek(index) else null
