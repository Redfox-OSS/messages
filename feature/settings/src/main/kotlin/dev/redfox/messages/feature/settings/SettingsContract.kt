package dev.redfox.messages.feature.settings

import androidx.compose.runtime.Immutable
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.model.VibrationPattern
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState

@Immutable
data class SettingsState(
    val accent: AppAccent = AppAccent.Default,
    val isDefaultSmsApp: Boolean = false,
    val swipeLeft: SwipeAction = SwipeAction.Archive,
    val swipeRight: SwipeAction = SwipeAction.ToggleRead,
    val vibration: VibrationPattern = VibrationPattern.Default,
) : UiState

sealed interface SettingsIntent : UiIntent {
    data object Refresh : SettingsIntent
    data class AccentSelected(val accent: AppAccent) : SettingsIntent
    data class SwipeActionSelected(val left: Boolean, val action: SwipeAction) : SettingsIntent
    data class VibrationSelected(val pattern: VibrationPattern) : SettingsIntent
}

/** Settings has no one-off effects; everything it shows is state. */
sealed interface SettingsEffect : UiEffect
