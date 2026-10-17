package dev.redfox.messages.core.data

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.PagingSource
import androidx.paging.PagingState
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.contacts.ContactResolver
import dev.redfox.messages.core.database.ConversationDao
import dev.redfox.messages.core.database.ConversationEntity
import dev.redfox.messages.core.database.SearchDao
import dev.redfox.messages.core.database.SearchDocEntity
import dev.redfox.messages.core.domain.SearchQuery
import dev.redfox.messages.core.domain.repository.SearchRepository
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageBox
import dev.redfox.messages.core.model.MessageCategory
import dev.redfox.messages.core.model.MessageKind
import dev.redfox.messages.core.model.SearchHit
import dev.redfox.messages.core.model.SearchResults
import dev.redfox.messages.core.telephony.read.TelephonyReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.time.measureTimedValue

/**
 * Search over people (the inbox index: names and numbers) and message text (the full-text
 * index, a page at a time). Until the first full index pass has finished, message text is
 * searched in the system store directly, as one capped page.
 */
internal class SearchRepositoryImpl @Inject constructor(
    private val dao: ConversationDao,
    private val searchDao: SearchDao,
    private val indexer: SearchIndexer,
    private val reader: TelephonyReader,
    private val contacts: ContactResolver,
    @param:Dispatcher(MessagesDispatcher.IO) private val io: CoroutineDispatcher,
) : SearchRepository {

    override suspend fun search(query: String, conversationLimit: Int): SearchResults =
        withContext(io) {
            coroutineScope {
                val people = async {
                    dao.searchParticipants("%" + likeEscape(query) + "%", conversationLimit).map { it.toConversation() }
                }
                val count = async { countMessages(query) }
                SearchResults(query = query, conversations = people.await(), messageCount = count.await())
            }
        }

    override fun messages(query: String): Flow<PagingData<SearchHit>> =
        Pager(
            config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, prefetchDistance = PAGE_SIZE / 2, enablePlaceholders = false),
            pagingSourceFactory = { HitsSource(query) },
        ).flow

    /** Null when not known: the index isn't built yet, or the store is unreadable. */
    private suspend fun countMessages(query: String): Int? {
        val parsed = SearchQuery.parse(query)
        if (parsed.isEmpty) return 0
        if (!indexer.isReady()) return null
        return try {
            searchDao.count(parsed.toMatch())
        } catch (e: RuntimeException) {
            Log.w(TAG, "Search count failed", e)
            null
        }
    }

    private inner class HitsSource(private val query: String) : PagingSource<Int, SearchHit>() {
        private val parsed = SearchQuery.parse(query)

        override fun getRefreshKey(state: PagingState<Int, SearchHit>): Int? = null

        override suspend fun load(params: LoadParams<Int>): LoadResult<Int, SearchHit> = withContext(io) {
            try {
                if (parsed.isEmpty) return@withContext LoadResult.Page(emptyList(), null, null)
                if (!indexer.isReady()) {
                    // Still indexing (first launch): one capped scan of the store, like before.
                    val found = reader.searchMessages(query, FALLBACK_LIMIT)
                    return@withContext LoadResult.Page(withThreads(found), prevKey = null, nextKey = null)
                }
                val offset = params.key ?: 0
                val (docs, took) = measureTimedValue { searchDao.page(parsed.toMatch(), params.loadSize, offset) }
                Log.d(TAG, "“$query” page @$offset: ${docs.size} in $took")
                LoadResult.Page(
                    data = withThreads(docs.map { it.toMessage() }),
                    prevKey = null,
                    nextKey = if (docs.size < params.loadSize) null else offset + docs.size,
                )
            } catch (e: RuntimeException) {
                Log.w(TAG, "Message search failed", e)
                LoadResult.Error(e)
            }
        }
    }

    /** One lookup per page for the conversations and the senders' contact names. */
    private suspend fun withThreads(messages: List<Message>): List<SearchHit> {
        if (messages.isEmpty()) return emptyList()
        val threads = dao.byIds(messages.map { it.threadId }.distinct()).associate { it.threadId to it.toConversation() }
        val senders = contacts.resolveAll(messages.mapNotNull { it.sender?.address }.distinct())
        return messages.map { m -> SearchHit(m.copy(sender = m.sender?.let { senders[it.address] ?: it }), threads[m.threadId]) }
    }

    private fun SearchDocEntity.toMessage() = Message(
        id = messageId,
        threadId = threadId,
        kind = if (kind == SearchDocEntity.KIND_MMS) MessageKind.Mms else MessageKind.Sms,
        box = if (outgoing) MessageBox.Sent else MessageBox.Inbox,
        sender = address?.let(::Contact),
        body = body,
        timestamp = timestamp,
        isRead = true,
        subscriptionId = -1,
    )

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
        const val TAG = "SearchRepository"
        const val PAGE_SIZE = 30
        const val FALLBACK_LIMIT = 120

        /** LIKE wildcards in the query match literally (the DAO uses ESCAPE '\'). */
        fun likeEscape(text: String) = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class SearchDataModule {
    @Binds
    abstract fun searchRepository(impl: SearchRepositoryImpl): SearchRepository
}
