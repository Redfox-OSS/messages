package dev.redfox.messages.core.domain.repository

import androidx.paging.PagingData
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Messages, paged. The inbox pages from the app's own index (kept in sync with the system
 * SMS/MMS store in the background); a thread pages straight from the store. Both re-page
 * live when the store changes.
 */
interface MessagesRepository {
    /** Starts keeping the inbox index in sync. Idempotent; needs READ_SMS. */
    fun startSync()

    fun observeSyncStatus(): Flow<SyncStatus>

    fun pagedConversations(filter: InboxFilter): Flow<PagingData<Conversation>>

    fun observeConversation(threadId: Long): Flow<Conversation?>

    /** Newest first. */
    fun pagedMessages(threadId: Long): Flow<PagingData<Message>>
}
