package dev.redfox.messages.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.AppSettings
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.model.VibrationPattern
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Typed access to the app's settings. The only place that knows the storage keys. */
@Singleton
class SettingsDataSource @Inject constructor(
    private val store: DataStore<Preferences>,
) {
    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            accent = prefs[Keys.Accent]
                ?.let { name -> AppAccent.entries.firstOrNull { it.name == name } }
                ?: AppAccent.Default,
            onboardingCompleted = prefs[Keys.OnboardingCompleted] ?: false,
            swipeLeft = prefs[Keys.SwipeLeft].toEnum(AppSettings().swipeLeft),
            swipeRight = prefs[Keys.SwipeRight].toEnum(AppSettings().swipeRight),
            vibration = prefs[Keys.Vibration].toEnum(AppSettings().vibration),
        )
    }

    /** [left] is the right-to-left swipe. */
    suspend fun setSwipeAction(left: Boolean, action: SwipeAction) {
        store.edit { it[if (left) Keys.SwipeLeft else Keys.SwipeRight] = action.name }
    }

    suspend fun setVibration(pattern: VibrationPattern) {
        store.edit { it[Keys.Vibration] = pattern.name }
    }

    suspend fun setAccent(accent: AppAccent) {
        store.edit { it[Keys.Accent] = accent.name }
    }

    suspend fun setOnboardingCompleted(completed: Boolean) {
        store.edit { it[Keys.OnboardingCompleted] = completed }
    }

    private object Keys {
        val Accent = stringPreferencesKey("accent")
        val OnboardingCompleted = booleanPreferencesKey("onboarding_completed")
        val SwipeLeft = stringPreferencesKey("swipe_left")
        val SwipeRight = stringPreferencesKey("swipe_right")
        val Vibration = stringPreferencesKey("notification_vibration")
    }
}

/** Stored enum names survive renames gracefully: an unknown name falls back to the default. */
private inline fun <reified E : Enum<E>> String?.toEnum(default: E): E =
    this?.let { name -> enumValues<E>().firstOrNull { it.name == name } } ?: default

@Module
@InstallIn(SingletonComponent::class)
object DataStoreModule {
    @Provides
    @Singleton
    fun settingsStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsStore
}
