package dev.redfox.messages.feature.settings

import androidx.compose.runtime.Immutable
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState

@Immutable
data class SettingsState(
    val accent: AppAccent = AppAccent.Default,
    val isDefaultSmsApp: Boolean = false,
) : UiState

sealed interface SettingsIntent : UiIntent {
    data object Refresh : SettingsIntent
    data class AccentSelected(val accent: AppAccent) : SettingsIntent
    data object DesignShowcaseClicked : SettingsIntent
}

sealed interface SettingsEffect : UiEffect {
    data object OpenDesignShowcase : SettingsEffect
}
