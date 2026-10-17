package dev.redfox.messages

import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.ObserveSettingsUseCase
import dev.redfox.messages.core.domain.usecase.ResolveThreadUseCase
import dev.redfox.messages.core.domain.usecase.SetAccentUseCase
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.AppSettings
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import javax.inject.Inject

@Immutable
data class MainState(
    /** Null until the first settings read, so the shell never flashes the wrong start screen or accent. */
    val settings: AppSettings? = null,
) : UiState

sealed interface MainIntent : UiIntent {
    data class AccentChanged(val accent: AppAccent) : MainIntent

    /**
     * The app was opened to a conversation from outside: a notification (thread known) or
     * another app's `sms:` link (addresses known, maybe a pre-filled [body]). With neither,
     * it's a plain share of text, which opens the recipient picker.
     */
    data class OpenExternal(val threadId: Long, val addresses: List<String>, val body: String?) : MainIntent
}

sealed interface MainEffect : UiEffect {
    data class OpenThread(val threadId: Long, val addresses: List<String>, val draft: String?) : MainEffect
    data class PickRecipient(val draft: String?) : MainEffect
}

/** App shell state: the theme accent and the start destination. */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val setAccent: SetAccentUseCase,
    private val resolveThread: ResolveThreadUseCase,
) : MviViewModel<MainState, MainIntent, MainEffect>(MainState()) {

    init {
        observeSettings().bind { copy(settings = it) }
    }

    override suspend fun handle(intent: MainIntent) {
        when (intent) {
            is MainIntent.AccentChanged -> setAccent(intent.accent)
            is MainIntent.OpenExternal -> openExternal(intent)
        }
    }

    private suspend fun openExternal(intent: MainIntent.OpenExternal) {
        when {
            intent.threadId > 0 -> emit(MainEffect.OpenThread(intent.threadId, intent.addresses, intent.body))
            intent.addresses.isNotEmpty() -> {
                val threadId = runCatching { resolveThread(intent.addresses) }.getOrNull() ?: return
                emit(MainEffect.OpenThread(threadId, intent.addresses, intent.body))
            }
            !intent.body.isNullOrBlank() -> emit(MainEffect.PickRecipient(intent.body))
        }
    }
}
