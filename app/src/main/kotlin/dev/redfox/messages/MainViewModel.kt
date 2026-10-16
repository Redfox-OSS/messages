package dev.redfox.messages

import androidx.compose.runtime.Immutable
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.ObserveSettingsUseCase
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
}

sealed interface MainEffect : UiEffect

/** App shell state: the theme accent and the start destination. */
@HiltViewModel
class MainViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val setAccent: SetAccentUseCase,
) : MviViewModel<MainState, MainIntent, MainEffect>(MainState()) {

    init {
        observeSettings().bind { copy(settings = it) }
    }

    override suspend fun handle(intent: MainIntent) {
        when (intent) {
            is MainIntent.AccentChanged -> setAccent(intent.accent)
        }
    }
}
