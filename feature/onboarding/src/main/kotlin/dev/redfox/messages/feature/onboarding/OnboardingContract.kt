package dev.redfox.messages.feature.onboarding

import androidx.compose.runtime.Immutable
import dev.redfox.messages.core.model.DeviceSetupStatus
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState

@Immutable
data class OnboardingState(
    val status: DeviceSetupStatus = DeviceSetupStatus.Unknown,
    val isFinishing: Boolean = false,
) : UiState {
    /** Only the SMS role is mandatory; permissions and battery are strongly recommended. */
    val canContinue: Boolean get() = status.isDefaultSmsApp && !isFinishing
}

sealed interface OnboardingIntent : UiIntent {
    /** The screen resumed or a system dialog returned: re-read role / permission state. */
    data object Refresh : OnboardingIntent
    data object DefaultSmsClicked : OnboardingIntent
    data object PermissionsClicked : OnboardingIntent
    data object BatteryClicked : OnboardingIntent
    data object ContinueClicked : OnboardingIntent
}

sealed interface OnboardingEffect : UiEffect {
    data object RequestDefaultSmsRole : OnboardingEffect
    data object RequestRuntimePermissions : OnboardingEffect
    data object RequestBatteryExemption : OnboardingEffect
    data object Finished : OnboardingEffect
}
