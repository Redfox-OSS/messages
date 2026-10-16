package dev.redfox.messages.feature.onboarding

import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.CompleteOnboardingUseCase
import dev.redfox.messages.core.domain.usecase.GetDeviceSetupStatusUseCase
import dev.redfox.messages.core.ui.mvi.MviViewModel
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val getDeviceSetupStatus: GetDeviceSetupStatusUseCase,
    private val completeOnboarding: CompleteOnboardingUseCase,
) : MviViewModel<OnboardingState, OnboardingIntent, OnboardingEffect>(
    OnboardingState(status = getDeviceSetupStatus())
) {

    override suspend fun handle(intent: OnboardingIntent) {
        when (intent) {
            OnboardingIntent.Refresh -> reduce { copy(status = getDeviceSetupStatus()) }

            OnboardingIntent.DefaultSmsClicked ->
                if (!currentState.status.isDefaultSmsApp) emit(OnboardingEffect.RequestDefaultSmsRole)

            OnboardingIntent.PermissionsClicked ->
                if (!currentState.status.hasRuntimePermissions) emit(OnboardingEffect.RequestRuntimePermissions)

            OnboardingIntent.BatteryClicked ->
                if (!currentState.status.isIgnoringBatteryOptimizations) emit(OnboardingEffect.RequestBatteryExemption)

            OnboardingIntent.ContinueClicked -> {
                if (!currentState.canContinue) return
                reduce { copy(isFinishing = true) }
                completeOnboarding()
                emit(OnboardingEffect.Finished)
            }
        }
    }
}
