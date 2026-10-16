package dev.redfox.messages.core.domain.usecase

import androidx.paging.PagingData
import dev.redfox.messages.core.domain.repository.MessagesRepository
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

/** Inbox filter chips. [All] and [Unread] cut across categories. */
enum class InboxFilter { All, Unread, Personal, Transactions, Otp, Offers }

class StartInboxSyncUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    operator fun invoke() = repository.startSync()
}

class ObserveSyncStatusUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    operator fun invoke(): Flow<SyncStatus> = repository.observeSyncStatus()
}

class PagedConversationsUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    operator fun invoke(filter: InboxFilter): Flow<PagingData<Conversation>> = repository.pagedConversations(filter)
}

class ObserveConversationUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    operator fun invoke(threadId: Long): Flow<Conversation?> = repository.observeConversation(threadId)
}

class PagedMessagesUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    operator fun invoke(threadId: Long): Flow<PagingData<Message>> = repository.pagedMessages(threadId)
}
