package dev.redfox.messages.core.domain.usecase

import dev.redfox.messages.core.domain.repository.DeviceSetupRepository
import dev.redfox.messages.core.domain.repository.SettingsRepository
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.AppSettings
import dev.redfox.messages.core.model.DeviceSetupStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveSettingsUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    operator fun invoke(): Flow<AppSettings> = repository.settings
}

class SetAccentUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke(accent: AppAccent) = repository.setAccent(accent)
}

class CompleteOnboardingUseCase @Inject constructor(
    private val repository: SettingsRepository,
) {
    suspend operator fun invoke() = repository.setOnboardingCompleted(true)
}

class GetDeviceSetupStatusUseCase @Inject constructor(
    private val repository: DeviceSetupRepository,
) {
    operator fun invoke(): DeviceSetupStatus = repository.currentStatus()
}
