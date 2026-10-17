package dev.redfox.messages.core.data

import android.util.Log
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.contacts.ContactResolver
import dev.redfox.messages.core.database.ConversationDao
import dev.redfox.messages.core.domain.repository.SearchRepository
import dev.redfox.messages.core.model.MessageCategory
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.SearchHit
import dev.redfox.messages.core.model.SearchResults
import dev.redfox.messages.core.telephony.read.TelephonyReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Search over people (the inbox index: names and numbers) and message text (the SMS/MMS store,
 * which is the source of truth). Both run in parallel off the main thread.
 */
internal class SearchRepositoryImpl @Inject constructor(
    private val dao: ConversationDao,
    private val reader: TelephonyReader,
    private val contacts: ContactResolver,
    @param:Dispatcher(MessagesDispatcher.IO) private val io: CoroutineDispatcher,
) : SearchRepository {

    override suspend fun search(query: String, conversationLimit: Int, messageLimit: Int): SearchResults =
        withContext(io) {
            coroutineScope {
                val people = async {
                    dao.searchParticipants("%" + likeEscape(query) + "%", conversationLimit).map { it.toConversation() }
                }
                val texts = async {
                    try {
                        reader.searchMessages(query, messageLimit)
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "Message search failed", e)
                        emptyList()
                    }
                }
                val messages = texts.await()
                val threads = dao.byIds(messages.map { it.threadId }.distinct()).associate { it.threadId to it.toConversation() }
                val senders = contacts.resolveAll(messages.mapNotNull { it.sender?.address })
                SearchResults(
                    query = query,
                    conversations = people.await(),
                    messages = messages.map { m ->
                        SearchHit(m.copy(sender = m.sender?.let { senders[it.address] ?: it }), threads[m.threadId])
                    },
                )
            }
        }

    private fun dev.redfox.messages.core.database.ConversationEntity.toConversation() = Conversation(
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
