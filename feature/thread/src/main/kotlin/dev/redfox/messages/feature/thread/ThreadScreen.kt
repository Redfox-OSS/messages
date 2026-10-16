package dev.redfox.messages.feature.thread

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.core.net.toUri
import android.content.Intent
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.redfox.messages.core.ui.components.BubblePosition
import dev.redfox.messages.core.ui.components.DateDivider
import dev.redfox.messages.core.ui.components.DetailPage
import dev.redfox.messages.core.ui.components.MessageBubble
import dev.redfox.messages.core.ui.components.RichCardBubble
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.obsidian.effects.glassEffect
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import kotlinx.serialization.Serializable

@Serializable
data class ThreadKey(val threadId: Long) : NavKey

/** Only the newest screenful plays the entrance; older history just appears. */
private const val RevealedRows = 12

@Composable
fun ThreadRoute(
    key: ThreadKey,
    onBack: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(
        creationCallback = { factory -> factory.create(key.threadId) }
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ThreadEffect.OpenUrl -> runCatching { uriHandler.openUri(effect.url) }
            is ThreadEffect.Dial -> runCatching {
                // ACTION_DIAL only pre-fills the dialler: no call permission, the user confirms.
                context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${effect.phoneNumber}".toUri()))
            }
        }
    }
    ThreadScreen(state = state, rows = rows, timeFormat = viewModel.timeFormat, onBack = onBack, onIntent = viewModel::dispatch)
}

@Composable
fun ThreadScreen(
    state: ThreadState,
    rows: LazyPagingItems<ThreadRow>,
    timeFormat: TimeFormat,
    onBack: () -> Unit,
    onIntent: (ThreadIntent) -> Unit,
) {
    val listState = rememberLazyListState()

    DetailPage(title = state.title, onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.glassSource().fillMaxSize(),
                // Reversed: "top" padding sits under the header, "bottom" above the read-only bar.
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 48.dp,
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
            ReadOnlyBar(Modifier.align(Alignment.BottomCenter))
        }
    }
}

private fun <T : Any> LazyPagingItems<T>.peekOrNull(index: Int): T? =
    if (index in 0 until itemCount) peek(index) else null

/** Placeholder where the composer lands in M3, on the same glass as the headers. */
@Composable
private fun ReadOnlyBar(modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    Box(
        modifier
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(bottom = ObSpacing.M)
            .clip(CircleShape)
            .glassEffect(colors.navScrim)
            .background(colors.border)
            .padding(horizontal = ObSpacing.L, vertical = ObSpacing.S),
    ) {
        Text(
            "Read-only · replying arrives in the next update",
            style = ObsidianTheme.typography.label,
            color = colors.textSecondary,
        )
    }
}
