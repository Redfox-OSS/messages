package dev.redfox.messages.feature.scheduled

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
import dev.redfox.messages.core.domain.usecase.ManageScheduledUseCase
import dev.redfox.messages.core.domain.usecase.ObserveScheduledUseCase
import dev.redfox.messages.core.model.ScheduledMessage
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.SchedulePicker
import dev.redfox.messages.core.ui.components.TabPage
import dev.redfox.messages.core.ui.components.formatScheduleTime
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.components.HeaderIconButton
import dev.redfox.obsidian.components.ObAlertDialog
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
data object ScheduledKey : NavKey

@Immutable
data class ScheduledState(
    val items: ImmutableList<ScheduledMessage> = persistentListOf(),
    val loaded: Boolean = false,
    /** The message whose time is being changed. */
    val rescheduling: ScheduledMessage? = null,
    val confirmCancel: ScheduledMessage? = null,
) : UiState

sealed interface ScheduledIntent : UiIntent {
    data class Open(val message: ScheduledMessage) : ScheduledIntent
    data class SendNow(val message: ScheduledMessage) : ScheduledIntent
    data class ChangeTime(val message: ScheduledMessage) : ScheduledIntent
    data class TimePicked(val sendAt: Long) : ScheduledIntent
    data object DismissPicker : ScheduledIntent
    data class Cancel(val message: ScheduledMessage) : ScheduledIntent
    data object ConfirmCancel : ScheduledIntent
    data object KeepScheduled : ScheduledIntent
}

sealed interface ScheduledEffect : UiEffect {
    data class OpenThread(val threadId: Long) : ScheduledEffect
}

/** Every scheduled message, soonest first, with send-now / change time / cancel. */
@HiltViewModel
class ScheduledViewModel @Inject constructor(
    observeScheduled: ObserveScheduledUseCase,
    private val manage: ManageScheduledUseCase,
) : MviViewModel<ScheduledState, ScheduledIntent, ScheduledEffect>(ScheduledState()) {

    init {
        observeScheduled().bind { copy(items = it.toImmutableList(), loaded = true) }
    }

    override suspend fun handle(intent: ScheduledIntent) {
        when (intent) {
            is ScheduledIntent.Open -> emit(ScheduledEffect.OpenThread(intent.message.threadId))
            is ScheduledIntent.SendNow -> manage.sendNow(intent.message.id)
            is ScheduledIntent.ChangeTime -> reduce { copy(rescheduling = intent.message) }
            ScheduledIntent.DismissPicker -> reduce { copy(rescheduling = null) }
            is ScheduledIntent.TimePicked -> {
                val message = state.value.rescheduling ?: return
                reduce { copy(rescheduling = null) }
                manage.reschedule(message.id, intent.sendAt)
            }
            is ScheduledIntent.Cancel -> reduce { copy(confirmCancel = intent.message) }
            ScheduledIntent.KeepScheduled -> reduce { copy(confirmCancel = null) }
            ScheduledIntent.ConfirmCancel -> {
                val message = state.value.confirmCancel ?: return
                reduce { copy(confirmCancel = null) }
                manage.cancel(message.id)
            }
        }
    }
}

@Composable
fun ScheduledRoute(
    onOpenThread: (threadId: Long) -> Unit,
    viewModel: ScheduledViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ScheduledEffect.OpenThread -> onOpenThread(effect.threadId)
        }
    }
    ScheduledScreen(state, viewModel::dispatch)
}

@Composable
fun ScheduledScreen(state: ScheduledState, onIntent: (ScheduledIntent) -> Unit) {
    TabPage(title = "Scheduled") { padding ->
        if (state.loaded && state.items.isEmpty()) {
            Box(Modifier.glassSource().fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = R.drawable.ic_schedule,
                    title = "Nothing scheduled",
                    subtitle = "Long-press the send button in a conversation to send a message later.",
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
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, message ->
                    ScheduledCard(
                        message = message,
                        onIntent = onIntent,
                        modifier = Modifier.animateItem().then(if (index < 10) Modifier.revealOnEnter(index) else Modifier),
                    )
                }
            }
        }
    }

    if (state.rescheduling != null) {
        SchedulePicker(
            title = "Change time",
            onPick = { onIntent(ScheduledIntent.TimePicked(it)) },
            onDismiss = { onIntent(ScheduledIntent.DismissPicker) },
        )
    }
    state.confirmCancel?.let { message ->
        ObAlertDialog(
            title = "Cancel scheduled message?",
            text = "The message to ${message.title} won't be sent.",
            confirmLabel = "Cancel message",
            dismissLabel = "Keep",
            destructive = true,
            onConfirm = { onIntent(ScheduledIntent.ConfirmCancel) },
            onDismiss = { onIntent(ScheduledIntent.KeepScheduled) },
        )
    }
}

@Composable
private fun ScheduledCard(message: ScheduledMessage, onIntent: (ScheduledIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    ObCard(modifier.fillMaxWidth().pressable(pressedScale = 0.98f) { onIntent(ScheduledIntent.Open(message)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(message.title, style = type.labelStrong, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (message.failed) "Couldn't send at ${formatScheduleTime(message.sendAt)}" else formatScheduleTime(message.sendAt),
                        style = type.caption,
                        color = if (message.failed) colors.error else colors.accentLight,
                    )
                }
                HeaderIconButton(R.drawable.ic_send, contentDescription = "Send now", onClick = { onIntent(ScheduledIntent.SendNow(message)) }, tint = colors.textSecondary)
                HeaderIconButton(R.drawable.ic_schedule, contentDescription = "Change time", onClick = { onIntent(ScheduledIntent.ChangeTime(message)) }, tint = colors.textSecondary)
                HeaderIconButton(R.drawable.ic_delete, contentDescription = "Cancel", onClick = { onIntent(ScheduledIntent.Cancel(message)) }, tint = colors.textSecondary)
            }
            Text(message.body, style = type.body, color = colors.textPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}
