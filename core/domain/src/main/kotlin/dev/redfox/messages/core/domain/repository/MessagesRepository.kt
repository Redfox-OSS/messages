package dev.redfox.messages.core.domain.repository

import androidx.paging.PagingData
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.SimCard
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

    /**
     * Sends [body] as SMS to each address separately (one thread each) from the given SIM.
     * The message appears in its thread straight away as "Sending…" and updates as the
     * network reports back. Needs the default SMS role.
     */
    suspend fun sendSms(addresses: List<String>, body: String, subscriptionId: Int)

    /** Re-sends a failed outgoing SMS in place. */
    suspend fun retry(message: Message)

    /** Marks every message in the thread read and clears its notification. */
    suspend fun markRead(threadId: Long)

    /** The system thread for these addresses, created if it doesn't exist yet. */
    suspend fun threadIdFor(addresses: List<String>): Long
}

/** SIMs that can send, for the composer's SIM picker. */
interface SimRepository {
    suspend fun activeSims(): List<SimCard>

    /** The system's default SIM for SMS, or null when there is none ("ask every time"). */
    suspend fun defaultSmsSubscriptionId(): Int?
}

/** Phone-book search for picking recipients. */
interface ContactsRepository {
    /** People whose name or number matches [query]; everyone (A–Z) when it's blank. One entry per number. */
    suspend fun search(query: String, limit: Int): List<Contact>
}
