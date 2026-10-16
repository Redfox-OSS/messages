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
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.contacts.ContactResolver
import dev.redfox.messages.core.database.ConversationDao
import dev.redfox.messages.core.database.ConversationEntity
import dev.redfox.messages.core.domain.repository.MessagesRepository
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageCategory
import dev.redfox.messages.core.model.SyncStatus
import dev.redfox.messages.core.telephony.read.MessagePageKey
import dev.redfox.messages.core.telephony.read.ProviderChanges
import dev.redfox.messages.core.telephony.read.TelephonyReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
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
    private val reader: TelephonyReader,
    private val contacts: ContactResolver,
    private val changes: ProviderChanges,
    @param:Dispatcher(MessagesDispatcher.IO) private val io: CoroutineDispatcher,
) : MessagesRepository {

    override fun startSync() = indexer.start()

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
    )

    private companion object {
        const val TAG = "MessagesRepository"

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
