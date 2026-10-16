package dev.redfox.messages.feature.settings

import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.GetDeviceSetupStatusUseCase
import dev.redfox.messages.core.domain.usecase.ObserveSettingsUseCase
import dev.redfox.messages.core.domain.usecase.SetAccentUseCase
import dev.redfox.messages.core.ui.mvi.MviViewModel
import javax.inject.Inject

/** Lightweight (MVVM-flavoured): state is bound from the settings flow; intents are thin writes. */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeSettings: ObserveSettingsUseCase,
    private val setAccent: SetAccentUseCase,
    private val getDeviceSetupStatus: GetDeviceSetupStatusUseCase,
) : MviViewModel<SettingsState, SettingsIntent, SettingsEffect>(SettingsState()) {

    init {
        observeSettings().bind { copy(accent = it.accent) }
    }

    override suspend fun handle(intent: SettingsIntent) {
        when (intent) {
            SettingsIntent.Refresh -> reduce { copy(isDefaultSmsApp = getDeviceSetupStatus().isDefaultSmsApp) }
            is SettingsIntent.AccentSelected -> setAccent(intent.accent)
            SettingsIntent.DesignShowcaseClicked -> emit(SettingsEffect.OpenDesignShowcase)
        }
    }
}
