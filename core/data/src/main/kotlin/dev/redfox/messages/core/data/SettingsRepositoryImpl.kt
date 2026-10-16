package dev.redfox.messages.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.redfox.messages.core.datastore.SettingsDataSource
import dev.redfox.messages.core.domain.repository.SettingsRepository
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.AppSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import javax.inject.Inject

internal class SettingsRepositoryImpl @Inject constructor(
    private val dataSource: SettingsDataSource,
) : SettingsRepository {
    override val settings: Flow<AppSettings> = dataSource.settings.distinctUntilChanged()

    override suspend fun setAccent(accent: AppAccent) = dataSource.setAccent(accent)

    override suspend fun setOnboardingCompleted(completed: Boolean) =
        dataSource.setOnboardingCompleted(completed)
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {
    @Binds
    abstract fun settingsRepository(impl: SettingsRepositoryImpl): SettingsRepository
}
