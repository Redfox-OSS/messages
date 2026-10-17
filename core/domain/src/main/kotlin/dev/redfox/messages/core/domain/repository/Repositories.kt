package dev.redfox.messages.core.domain.repository

import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.AppSettings
import dev.redfox.messages.core.model.DeviceSetupStatus
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.ScheduledMessage
import dev.redfox.messages.core.model.StarredMessage
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.model.VibrationPattern
import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setAccent(accent: AppAccent)
    suspend fun setOnboardingCompleted(completed: Boolean)

    /** [left]: the right-to-left swipe. */
    suspend fun setSwipeAction(left: Boolean, action: SwipeAction)

    /** Also re-creates the message notification channel, since Android fixes a channel's vibration. */
    suspend fun setVibration(pattern: VibrationPattern)
}

interface StarredRepository {
    fun observeAll(): Flow<List<StarredMessage>>
    fun observeKeys(threadId: Long): Flow<Set<String>>
    suspend fun star(message: Message, threadTitle: String)
    suspend fun unstar(key: String)
}

interface ScheduledRepository {
    fun observeAll(): Flow<List<ScheduledMessage>>
    fun observeForThread(threadId: Long): Flow<List<ScheduledMessage>>

    /** Stores the message and sets an exact alarm for [sendAt]. */
    suspend fun schedule(threadId: Long, recipients: List<String>, title: String, body: String, subscriptionId: Int, sendAt: Long)
    suspend fun cancel(id: Long)
    suspend fun sendNow(id: Long)
    suspend fun reschedule(id: Long, sendAt: Long)
}

interface DeviceSetupRepository {
    /** Snapshot of role, permission and battery state. Cheap; call on every resume. */
    fun currentStatus(): DeviceSetupStatus
}
