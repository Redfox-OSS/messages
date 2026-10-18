package dev.redfox.messages.core.domain.usecase

import dev.redfox.messages.core.domain.repository.MessagesRepository
import dev.redfox.messages.core.domain.repository.ScheduledRepository
import dev.redfox.messages.core.domain.repository.SettingsRepository
import dev.redfox.messages.core.domain.repository.StarredRepository
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.ScheduledMessage
import dev.redfox.messages.core.model.StarredMessage
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.model.VibrationPattern
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveStarredUseCase @Inject constructor(private val starred: StarredRepository) {
    operator fun invoke(): Flow<List<StarredMessage>> = starred.observeAll()
}

class ObserveStarredKeysUseCase @Inject constructor(private val starred: StarredRepository) {
    operator fun invoke(threadId: Long): Flow<Set<String>> = starred.observeKeys(threadId)
}

class SetStarredUseCase @Inject constructor(private val starred: StarredRepository) {
    suspend operator fun invoke(message: Message, threadTitle: String, starred: Boolean) =
        if (starred) this.starred.star(message, threadTitle) else this.starred.unstar(message.key)
}

class UnstarUseCase @Inject constructor(private val starred: StarredRepository) {
    suspend operator fun invoke(key: String) = starred.unstar(key)
}

class ObserveScheduledUseCase @Inject constructor(private val scheduled: ScheduledRepository) {
    operator fun invoke(): Flow<List<ScheduledMessage>> = scheduled.observeAll()
    fun forThread(threadId: Long): Flow<List<ScheduledMessage>> = scheduled.observeForThread(threadId)
}

class ScheduleMessageUseCase @Inject constructor(private val scheduled: ScheduledRepository) {
    /** Returns false for a blank message, no recipients, or a time that has already passed. */
    suspend operator fun invoke(
        threadId: Long,
        recipients: List<String>,
        title: String,
        body: String,
        subscriptionId: Int,
        sendAt: Long,
        now: Long = System.currentTimeMillis(),
    ): Boolean {
        if (body.isBlank() || recipients.isEmpty() || sendAt <= now) return false
        scheduled.schedule(threadId, recipients, title, body.trimEnd(), subscriptionId, sendAt)
        return true
    }
}

class ManageScheduledUseCase @Inject constructor(private val scheduled: ScheduledRepository) {
    suspend fun cancel(id: Long) = scheduled.cancel(id)
    suspend fun sendNow(id: Long) = scheduled.sendNow(id)
    suspend fun reschedule(id: Long, sendAt: Long) = scheduled.reschedule(id, sendAt)
}

/** Everything a swipe or menu can do to a whole conversation. */
class ConversationActionsUseCase @Inject constructor(private val messages: MessagesRepository) {
    suspend fun togglePinned(conversation: Conversation) = messages.setPinned(conversation.threadId, !conversation.isPinned)
    suspend fun toggleArchived(conversation: Conversation) =
        messages.setArchived(conversation.threadId, !conversation.isArchived)
    suspend fun toggleRead(conversation: Conversation) = messages.toggleRead(conversation.threadId)
    suspend fun markRead(conversation: Conversation) = messages.markRead(conversation.threadId)
    suspend fun setArchived(conversation: Conversation, archived: Boolean) =
        messages.setArchived(conversation.threadId, archived)
    suspend fun delete(conversation: Conversation) = messages.deleteThread(conversation.threadId)
}

class DeleteMessageUseCase @Inject constructor(private val messages: MessagesRepository) {
    suspend operator fun invoke(message: Message) = messages.deleteMessage(message)
}

class SetSwipeActionUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(left: Boolean, action: SwipeAction) = settings.setSwipeAction(left, action)
}

class SetVibrationUseCase @Inject constructor(private val settings: SettingsRepository) {
    suspend operator fun invoke(pattern: VibrationPattern) = settings.setVibration(pattern)
}
