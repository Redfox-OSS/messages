package dev.redfox.messages.core.data

import android.util.Log
import androidx.paging.InvalidatingPagingSourceFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import androidx.paging.map
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.contacts.ContactResolver
import dev.redfox.messages.core.database.ConversationDao
import dev.redfox.messages.core.database.ConversationEntity
import dev.redfox.messages.core.database.ConversationPrefsDao
import dev.redfox.messages.core.database.ConversationPrefsEntity
import dev.redfox.messages.core.domain.repository.MessagesRepository
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageCategory
import dev.redfox.messages.core.model.MessageKind
import dev.redfox.messages.core.model.SyncStatus
import dev.redfox.messages.core.telephony.IncomingMessageNotifier
import dev.redfox.messages.core.telephony.SmsProviderWriter
import dev.redfox.messages.core.telephony.send.SmsSender
import dev.redfox.messages.core.telephony.mms.MmsMedia
import dev.redfox.messages.core.telephony.mms.MmsTransport
import dev.redfox.messages.core.model.OutgoingAttachment
import android.content.ContentUris
import android.provider.Telephony
import dev.redfox.messages.core.telephony.read.MessagePageKey
import dev.redfox.messages.core.telephony.read.ProviderChanges
import dev.redfox.messages.core.telephony.read.TelephonyReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The inbox pages from the Room index ([ConversationIndexer] keeps it in step with the system
 * store); a thread pages straight from the store with keyset paging. Neither ever loads a
 * whole history at once.
 */
internal class MessagesRepositoryImpl @Inject constructor(
    private val indexer: ConversationIndexer,
    private val dao: ConversationDao,
    private val prefsDao: ConversationPrefsDao,
    private val reader: TelephonyReader,
    private val contacts: ContactResolver,
    private val changes: ProviderChanges,
    private val sender: SmsSender,
    private val mms: MmsTransport,
    private val media: MmsMedia,
    private val writer: SmsProviderWriter,
    private val notifier: IncomingMessageNotifier,
    @param:ApplicationScope private val appScope: CoroutineScope,
    @param:Dispatcher(MessagesDispatcher.IO) private val io: CoroutineDispatcher,
) : MessagesRepository {

    override fun startSync() {
        indexer.start()
        appScope.launch(io) {
            // Sends interrupted by a process death would otherwise say "Sending…" forever.
            runCatching { writer.failStaleOutbox(olderThanMillis = STALE_OUTBOX_MILLIS) }
                .onFailure { Log.w(TAG, "Couldn't check the outbox", it) }
        }
    }

    override suspend fun sendSms(addresses: List<String>, body: String, subscriptionId: Int) = withContext(io) {
        Log.w(TAG, "Sending SMS to ${addresses.size} recipient(s)")
        addresses.forEach { address ->
            // Nothing stored means nothing sent: say so, so the composer gives the text back.
            sender.send(address, body, subscriptionId) ?: error("Couldn't save the message")
        }
    }

    override suspend fun sendMms(
        addresses: List<String>,
        body: String,
        attachments: List<OutgoingAttachment>,
        subscriptionId: Int,
    ) = withContext(io) {
        try {
            val threadId = writer.threadIdFor(addresses.toSet())
            // Leave room for headers, SMIL and the text itself.
            val maxSize = mms.maxMessageSize(subscriptionId)
            val budget = (maxSize * MMS_BUDGET_SHARE).toInt() - body.toByteArray().size
            Log.w(TAG, "Preparing MMS: ${attachments.map { it.mimeType }} to ${addresses.size} recipient(s), limit $maxSize B")
            val parts = media.toParts(attachments, budget)
            Log.w(TAG, "MMS parts ready: ${parts.map { "${it.contentType} ${it.data.size} B" }}")
            mms.send(threadId, addresses, body.ifBlank { null }, parts, subscriptionId)
        } catch (e: Exception) {
            // Surfaces as the composer's error popup; the log has the cause.
            Log.e(TAG, "Couldn't prepare MMS", e)
            throw e
        }
        Unit
    }

    override suspend fun retry(message: Message) = withContext(io) {
        when (message.kind) {
            MessageKind.Sms -> sender.retry(message.id)
            MessageKind.Mms -> mms.resend(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, message.id))
        }
    }

    override suspend fun retryDownload(message: Message) = withContext(io) {
        mms.retryDownload(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, message.id))
    }

    override suspend fun maxMmsSize(subscriptionId: Int): Int = withContext(io) { mms.maxMessageSize(subscriptionId) }

    override suspend fun markRead(threadId: Long) = withContext(io) {
        try {
            writer.markThreadRead(threadId)
        } catch (e: RuntimeException) {
            Log.w(TAG, "Couldn't mark thread read", e)
        }
        dao.observe(threadId).first()?.let { notifier.cancelFor(ParticipantCodec.decode(it.participants).map { p -> p.address }) }
        Unit
    }

    override suspend fun threadIdFor(addresses: List<String>): Long = withContext(io) { writer.threadIdFor(addresses.toSet()) }

    override suspend fun setPinned(threadId: Long, pinned: Boolean) = updatePrefs(threadId) { copy(pinned = pinned) }

    override suspend fun setArchived(threadId: Long, archived: Boolean) = updatePrefs(threadId) { copy(archived = archived) }

    /** Saves the user's choice, and patches the index row so the inbox updates immediately. */
    private suspend fun updatePrefs(threadId: Long, change: ConversationPrefsEntity.() -> ConversationPrefsEntity) {
        val prefs = (prefsDao.get(threadId) ?: ConversationPrefsEntity(threadId)).change()
        if (prefs.pinned || prefs.archived) prefsDao.upsert(prefs) else prefsDao.delete(threadId)
        dao.setFlags(threadId, prefs.pinned, prefs.archived)
    }

    override suspend fun toggleRead(threadId: Long) = withContext(io) {
        val unread = (dao.observe(threadId).first()?.unreadCount ?: 0) > 0
        if (unread) markRead(threadId) else writer.markLatestIncomingUnread(threadId)
    }

    override suspend fun deleteThread(threadId: Long) = withContext(io) {
        writer.deleteThread(threadId)
        prefsDao.delete(threadId)
        dao.delete(listOf(threadId))
    }

    override suspend fun deleteMessage(message: Message) = withContext(io) {
        when (message.kind) {
            MessageKind.Sms -> writer.deleteSms(message.id)
            MessageKind.Mms -> writer.deleteMms(message.id)
        }
        Unit
    }

    override fun observeSyncStatus(): Flow<SyncStatus> = indexer.status

    override fun pagedConversations(filter: InboxFilter): Flow<PagingData<Conversation>> =
        Pager(InboxPaging) {
            when (filter) {
                InboxFilter.All -> dao.pageAll()
                InboxFilter.Unread -> dao.pageUnread()
                InboxFilter.Personal -> dao.pageByCategory(MessageCategory.Personal.name)
                InboxFilter.Transactions -> dao.pageByCategory(MessageCategory.Transactions.name)
                InboxFilter.Otp -> dao.pageByCategory(MessageCategory.Otp.name)
                InboxFilter.Offers -> dao.pageByCategory(MessageCategory.Offers.name)
                InboxFilter.Archived -> dao.pageArchived()
            }
        }.flow.map { page -> page.map { it.toConversation() } }

    override fun observeConversation(threadId: Long): Flow<Conversation?> =
        dao.observe(threadId).map { it?.toConversation() }.distinctUntilChanged()

    override fun pagedMessages(threadId: Long): Flow<PagingData<Message>> = channelFlow {
        val sources = InvalidatingPagingSourceFactory { ThreadPagingSource(threadId) }
        // Re-page when the store changes (a new message arrives, another app deletes one…).
        launch { changes.ticks.drop(1).collect { sources.invalidate() } }
        Pager(ThreadPaging, pagingSourceFactory = sources).flow.collect { send(it) }
    }

    /** Pages a thread newest → oldest straight from the SMS/MMS store. */
    private inner class ThreadPagingSource(private val threadId: Long) : PagingSource<MessagePageKey, Message>() {

        override suspend fun load(params: LoadParams<MessagePageKey>): LoadResult<MessagePageKey, Message> =
            withContext(io) {
                try {
                    val page = reader.readPage(threadId, params.key, params.loadSize)
                    val senders = contacts.resolveAll(page.messages.mapNotNull { it.sender?.address })
                    LoadResult.Page(
                        data = page.messages.map { m -> m.copy(sender = m.sender?.let { senders[it.address] }) },
                        prevKey = null, // Always starts from the newest; only older pages append.
                        nextKey = page.nextKey,
                    )
                } catch (e: RuntimeException) {
                    Log.e(TAG, "Thread page failed", e)
                    LoadResult.Error(e)
                }
            }

        /** A refresh (store changed) restarts from the newest message, where the user almost always is. */
        override fun getRefreshKey(state: PagingState<MessagePageKey, Message>): MessagePageKey? = null
    }

    private fun ConversationEntity.toConversation() = Conversation(
        threadId = threadId,
        participants = ParticipantCodec.decode(participants),
        snippet = snippet,
        timestamp = timestamp,
        unreadCount = unreadCount,
        messageCount = messageCount,
        hasAttachment = hasAttachment,
        category = runCatching { MessageCategory.valueOf(category) }.getOrDefault(MessageCategory.Personal),
        isPinned = pinned,
        isArchived = archived,
    )

    private companion object {
        const val TAG = "MessagesRepository"

        /** Radios report back within seconds; anything older that's still "sending" never will. */
        const val STALE_OUTBOX_MILLIS = 3 * 60_000L

        /** Share of the carrier's MMS limit given to media; the rest covers headers, SMIL and text. */
        const val MMS_BUDGET_SHARE = 0.9

        /** Rows are tiny; a generous prefetch keeps fast flings from ever reaching the end of loaded data. */
        val InboxPaging = PagingConfig(pageSize = 40, prefetchDistance = 20, initialLoadSize = 60, enablePlaceholders = false)

        /** First page fills ~2 screens of bubbles; older history streams in ahead of the scroll. */
        val ThreadPaging = PagingConfig(pageSize = 50, prefetchDistance = 30, initialLoadSize = 60, enablePlaceholders = false)
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class MessagesDataModule {
    @Binds
    abstract fun messagesRepository(impl: MessagesRepositoryImpl): MessagesRepository
}
