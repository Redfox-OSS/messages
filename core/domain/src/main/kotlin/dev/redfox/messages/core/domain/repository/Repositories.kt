package dev.redfox.messages.core.domain.repository

import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.AppSettings
import dev.redfox.messages.core.model.DeviceSetupStatus
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setAccent(accent: AppAccent)
    suspend fun setOnboardingCompleted(completed: Boolean)
}

interface DeviceSetupRepository {
    /** Snapshot of role, permission and battery state. Cheap; call on every resume. */
    fun currentStatus(): DeviceSetupStatus
}
