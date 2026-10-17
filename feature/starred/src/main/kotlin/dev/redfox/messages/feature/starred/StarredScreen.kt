package dev.redfox.messages.feature.starred

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.ObserveStarredUseCase
import dev.redfox.messages.core.domain.usecase.UnstarUseCase
import dev.redfox.messages.core.model.StarredMessage
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.LinkifiedText
import dev.redfox.messages.core.ui.components.TabPage
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.serialization.Serializable
import javax.inject.Inject

@Serializable
data object StarredKey : NavKey

@Immutable
data class StarredItem(val message: StarredMessage, val timeLabel: String)

@Immutable
data class StarredState(
    val items: ImmutableList<StarredItem> = persistentListOf(),
    val loaded: Boolean = false,
) : UiState

sealed interface StarredIntent : UiIntent {
    data class Open(val item: StarredItem) : StarredIntent
    data class Unstar(val item: StarredItem) : StarredIntent
}

sealed interface StarredEffect : UiEffect {
    data class OpenThread(val threadId: Long) : StarredEffect
}

/** Starred messages, newest star first (MVVM-light: a bound list plus two actions). */
@HiltViewModel
class StarredViewModel @Inject constructor(
    observeStarred: ObserveStarredUseCase,
    private val unstar: UnstarUseCase,
) : MviViewModel<StarredState, StarredIntent, StarredEffect>(StarredState()) {

    private val timeFormat = TimeFormat()

    init {
        observeStarred().bind { list ->
            val now = System.currentTimeMillis()
            copy(
                items = list.map { StarredItem(it, timeFormat.conversationTime(it.timestamp, now)) }.toImmutableList(),
                loaded = true,
            )
        }
    }

    override suspend fun handle(intent: StarredIntent) {
        when (intent) {
            is StarredIntent.Open -> emit(StarredEffect.OpenThread(intent.item.message.threadId))
            is StarredIntent.Unstar -> unstar(intent.item.message.key)
        }
    }
}

@Composable
fun StarredRoute(
    onOpenThread: (threadId: Long) -> Unit,
    viewModel: StarredViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is StarredEffect.OpenThread -> onOpenThread(effect.threadId)
        }
    }
    StarredScreen(state, viewModel::dispatch)
}

@Composable
fun StarredScreen(state: StarredState, onIntent: (StarredIntent) -> Unit) {
    TabPage(title = "Starred") { padding ->
        if (state.loaded && state.items.isEmpty()) {
            Box(Modifier.glassSource().fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = R.drawable.ic_star,
                    title = "Nothing starred",
                    subtitle = "Long-press any message and star it to keep it here.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.glassSource().fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                    start = ScreenGutter,
                    end = ScreenGutter,
                ),
                verticalArrangement = Arrangement.spacedBy(ObSpacing.M),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.message.key }) { index, item ->
                    StarredCard(
                        item = item,
                        onOpen = { onIntent(StarredIntent.Open(item)) },
                        onUnstar = { onIntent(StarredIntent.Unstar(item)) },
                        modifier = Modifier
                            .animateItem()
                            .then(if (index < 10) Modifier.revealOnEnter(index) else Modifier),
                    )
                }
            }
        }
    }
}

@Composable
private fun StarredCard(item: StarredItem, onOpen: () -> Unit, onUnstar: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val message = item.message
    ObCard(modifier.fillMaxWidth().pressable(pressedScale = 0.98f, onClick = onOpen)) {
        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(message.threadTitle, style = type.labelStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        (if (message.sender == null) "You · " else "") + item.timeLabel,
                        style = type.caption,
                        color = colors.textTertiary,
                    )
                }
                HeaderIconButton(R.drawable.ic_star_filled, contentDescription = "Unstar", onClick = onUnstar, tint = colors.accentLight)
            }
            LinkifiedText(message.body, style = type.body, color = colors.textPrimary)
        }
    }
}
