package dev.redfox.messages.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.Avatar
import dev.redfox.messages.core.ui.components.ConversationRow
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.obsidian.effects.ObGlows
import dev.redfox.obsidian.effects.glow
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.coroutines.delay

/** The nav bar's search pill size, where the field grows from (and shrinks back to). */
private val PillSize = 64.dp
private val FieldHeight = 56.dp
/** Matches the nav bar's gap above the gesture area, so the pill and field line up. */
private val BottomGap = 12.dp

/**
 * Search, without leaving the screen: the nav bar's accented search pill stretches to the left
 * into a full-width field that rides just above the keyboard, and results stack up above it
 * (nearest the thumb first). Back, or a tap outside the results, folds it back into the pill;
 * the chevron only puts the keyboard away.
 */
@Composable
fun SearchOverlay(
    open: Boolean,
    onClose: () -> Unit,
    onOpenThread: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val progress = remember { Animatable(0f) }
    val latestClose by rememberUpdatedState(onClose)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(open) {
        if (open) {
            progress.animateTo(1f, spring(dampingRatio = 0.82f, stiffness = 340f))
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
            progress.animateTo(0f, spring(dampingRatio = 1f, stiffness = 420f))
            viewModel.dispatch(SearchIntent.Closed)
        }
    }
    LaunchedEffect(open) {
        if (open) {
            delay(60) // Let the stretch start before the keyboard pushes everything up.
            // Retry briefly: the field may still be attaching on the first frames.
            repeat(5) {
                if (runCatching { focus.requestFocus() }.isSuccess) {
                    keyboard?.show()
                    return@LaunchedEffect
                }
                delay(40)
            }
        }
    }
    BackHandler(enabled = open) { latestClose() }

    val p = progress.value
    if (p <= 0f && !open) return

    val colors = ObsidianTheme.colors
    BoxWithConstraints(modifier.fillMaxSize()) {
        // Covers the page underneath (solid once open, fading in with the stretch); tapping it closes search.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = p }
                .background(colors.background)
                .pointerInput(Unit) { detectTapGestures { latestClose() } }
        )

        val insets = WindowInsets.ime.union(WindowInsets.navigationBars)
        Results(
            state = state,
            messages = messages,
            onOpenThread = { threadId ->
                onOpenThread(threadId)
                latestClose()
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(insets)
                .padding(bottom = BottomGap + FieldHeight + ObSpacing.M)
                .alpha(((p - 0.35f) / 0.65f).coerceIn(0f, 1f)),
        )

        val fullWidth = maxWidth - ScreenGutter * 2
        SearchField(
            progress = p,
            width = lerp(PillSize, fullWidth, p),
            query = state.query,
            onQueryChange = { viewModel.dispatch(SearchIntent.QueryChanged(it)) },
            onSubmit = {
                viewModel.dispatch(SearchIntent.Submit)
                keyboard?.hide()
            },
            onClear = { viewModel.dispatch(SearchIntent.Clear) },
            onHideKeyboard = {
                focusManager.clearFocus()
                keyboard?.hide()
            },
            focus = focus,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .windowInsetsPadding(insets)
                .padding(end = ScreenGutter, bottom = BottomGap),
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SearchField(
    progress: Float,
    width: Dp,
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
    /** Dismisses only the keyboard; search stays open (back or a tap outside closes it). */
    onHideKeyboard: () -> Unit,
    focus: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    // The pill's accent eases into the card surface as it becomes a field.
    val fill = lerp(colors.accent, colors.card, progress)
    val iconTint = lerp(colors.onAccent, colors.textSecondary, progress)
    val contentAlpha = ((progress - 0.55f) / 0.45f).coerceIn(0f, 1f)
    Row(
        modifier
            .width(width)
            .height(lerp(PillSize, FieldHeight, progress))
            .glow(lerp(colors.accent, Color.Black, progress), CircleShape, if (progress < 0.5f) ObGlows.Action else ObGlows.Elevation)
            .clip(CircleShape)
            .background(fill)
            .border(1.dp, colors.borderStrong.copy(alpha = colors.borderStrong.alpha * progress), CircleShape)
            .padding(start = lerp(20.dp, ObSpacing.L, progress), end = ObSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.S),
    ) {
        Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = iconTint, modifier = Modifier.size(22.dp))
        // The text field is always there (just invisible while the pill stretches), so it can
        // take focus, and bring up the keyboard, the moment search opens.
        Box(Modifier.weight(1f).alpha(contentAlpha)) {
            if (query.isEmpty()) {
                Text("Search messages and people", style = type.body, color = colors.textTertiary, maxLines = 1)
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                // The keyboard shows a search key; pressing it (or Enter) searches right away.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }, onDone = { onSubmit() }, onGo = { onSubmit() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (contentAlpha > 0f) {
            if (query.isNotEmpty()) {
                FieldButton(R.drawable.ic_close, "Clear search", onClear, Modifier.alpha(contentAlpha))
            }
            // Only while the keyboard is up: there's nothing to put away otherwise.
            if (WindowInsets.isImeVisible) {
                FieldButton(R.drawable.ic_keyboard_arrow_down, "Hide keyboard", onHideKeyboard, Modifier.alpha(contentAlpha))
            }
        }
    }
}

@Composable
private fun FieldButton(icon: Int, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(44.dp)
            .clip(CircleShape)
            .pressable(pressedScale = 0.9f, haptic = HapticFeedbackType.ContextClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = description, tint = ObsidianTheme.colors.textSecondary, modifier = Modifier.size(20.dp))
    }
}

/**
 * Results stacked upward from the field (reverse layout: the first item sits right above it).
 * People first, nearest the thumb, then matching messages, newest nearest.
 */
@Composable
private fun Results(
    state: SearchState,
    messages: LazyPagingItems<SearchRow>,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember { TimeFormat() }
    val results = state.results
    val now = remember(results) { System.currentTimeMillis() }
    // The count comes from the index; while it's still being built, from what has loaded.
    val messageCount = results?.messageCount
        ?: if (messages.loadState.refresh is LoadState.NotLoading) messages.itemCount else null
    val nothing = results != null && results.conversations.isEmpty() && messageCount == 0

    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = ObSpacing.S),
    ) {
        when {
            state.query.isBlank() -> item(key = "hint") {
                Notice("Search your messages", "Find words in any message, or a person by name or number.")
            }
            state.tooShort -> Unit
            results == null || results.query != state.query.trim() && nothing ->
                item(key = "searching") { Notice("Searching…", null) }
            nothing -> item(key = "none") {
                Notice("No results", "Nothing matches “${state.query.trim()}”.")
            }
            else -> {
                if (results.conversations.isNotEmpty()) {
                    items(results.conversations, key = { "c" + it.threadId }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            timeLabel = timeFormat.conversationTime(conversation.timestamp, now),
                            onClick = { onOpenThread(conversation.threadId) },
                        )
                    }
                    item(key = "people") { SectionTitle("People") }
                }
                if (messages.itemCount > 0) {
                    // Only rows near the screen are loaded; the rest page in while scrolling.
                    items(
                        count = messages.itemCount,
                        key = messages.itemKey { "m" + it.hit.message.key },
                        contentType = messages.itemContentType { "hit" },
                    ) { index ->
                        val row = messages[index] ?: return@items
                        HitRow(row) { onOpenThread(row.hit.message.threadId) }
                    }
                    item(key = "messages") {
                        SectionTitle(if (messageCount != null) "Messages · $messageCount" else "Messages")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = ObsidianTheme.typography.caption,
        color = ObsidianTheme.colors.textTertiary,
        modifier = Modifier.padding(start = ScreenGutter, top = ObSpacing.L, bottom = ObSpacing.XS),
    )
}

@Composable
private fun Notice(title: String, body: String?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = ScreenGutter, vertical = ObSpacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(ObSpacing.XS),
    ) {
        Text(title, style = ObsidianTheme.typography.bodyLarge, color = ObsidianTheme.colors.textPrimary, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = ObsidianTheme.typography.label, color = ObsidianTheme.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** One matching message: who, when, and the text around the match with the matches highlighted. */
@Composable
private fun HitRow(row: SearchRow, onClick: () -> Unit) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val hit = row.hit
    val message = hit.message
    val people = hit.conversation?.participants ?: listOfNotNull(message.sender)
    val title = hit.conversation?.title ?: message.sender?.displayName ?: "Unknown"
    val accent = colors.accentLight
    val snippet = remember(row.excerpt, accent) { row.excerpt.annotated(accent) }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.985f, onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = ObSpacing.S),
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.M),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(people, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (message.isOutgoing) "You → " else "") + title,
                    style = type.labelStrong,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(row.timeLabel, style = type.caption, color = colors.textTertiary)
            }
            Text(snippet, style = type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
