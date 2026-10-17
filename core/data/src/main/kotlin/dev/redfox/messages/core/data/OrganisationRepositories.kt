package dev.redfox.messages.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.data.scheduled.ScheduledAlarms
import dev.redfox.messages.core.data.scheduled.ScheduledSender
import dev.redfox.messages.core.database.ScheduledDao
import dev.redfox.messages.core.database.ScheduledMessageEntity
import dev.redfox.messages.core.database.StarredDao
import dev.redfox.messages.core.database.StarredMessageEntity
import dev.redfox.messages.core.domain.repository.ScheduledRepository
import dev.redfox.messages.core.domain.repository.StarredRepository
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.ScheduledMessage
import dev.redfox.messages.core.model.StarredMessage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class StarredRepositoryImpl @Inject constructor(
    private val dao: StarredDao,
) : StarredRepository {

    override fun observeAll(): Flow<List<StarredMessage>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeKeys(threadId: Long): Flow<Set<String>> =
        dao.observeKeys(threadId).map { it.toSet() }.distinctUntilChanged()

    override suspend fun star(message: Message, threadTitle: String) = dao.upsert(
        StarredMessageEntity(
            key = message.key,
            threadId = message.threadId,
            threadTitle = threadTitle,
            body = message.body,
            senderAddress = message.sender?.address,
            senderName = message.sender?.name,
            timestamp = message.timestamp,
            starredAt = System.currentTimeMillis(),
        )
    )

    override suspend fun unstar(key: String) = dao.delete(key)

    private fun StarredMessageEntity.toModel() = StarredMessage(
        key = key,
        threadId = threadId,
        threadTitle = threadTitle,
        body = body,
        sender = senderAddress?.let { Contact(address = it, name = senderName) },
        timestamp = timestamp,
        starredAt = starredAt,
    )
}

internal class ScheduledRepositoryImpl @Inject constructor(
    private val dao: ScheduledDao,
    private val alarms: ScheduledAlarms,
    private val sender: ScheduledSender,
    @param:Dispatcher(MessagesDispatcher.IO) private val io: CoroutineDispatcher,
) : ScheduledRepository {

    override fun observeAll(): Flow<List<ScheduledMessage>> = dao.observeAll().map { rows -> rows.map { it.toModel() } }

    override fun observeForThread(threadId: Long): Flow<List<ScheduledMessage>> =
        dao.observeForThread(threadId).map { rows -> rows.map { it.toModel() } }

    override suspend fun schedule(
        threadId: Long,
        recipients: List<String>,
        title: String,
        body: String,
        subscriptionId: Int,
        sendAt: Long,
    ) {
        val id = dao.insert(
            ScheduledMessageEntity(
                threadId = threadId,
                recipients = RecipientCodec.encode(recipients),
                title = title,
                body = body,
                subscriptionId = subscriptionId,
                sendAt = sendAt,
            )
        )
        alarms.set(id, sendAt)
    }

    override suspend fun cancel(id: Long) {
        alarms.cancel(id)
        dao.delete(id)
    }

    override suspend fun sendNow(id: Long) = withContext(io) {
        alarms.cancel(id)
        sender.send(id)
    }

    override suspend fun reschedule(id: Long, sendAt: Long) {
        dao.reschedule(id, sendAt)
        alarms.set(id, sendAt)
    }

    private fun ScheduledMessageEntity.toModel() = ScheduledMessage(
        id = id,
        threadId = threadId,
        recipients = RecipientCodec.decode(recipients),
        title = title,
        body = body,
        subscriptionId = subscriptionId,
        sendAt = sendAt,
        failed = failed,
    )
}

/** Recipient lists in one column, split by the ASCII unit separator (never in an address). */
internal object RecipientCodec {
    private const val SEPARATOR = '\u001F'
    fun encode(recipients: List<String>) = recipients.joinToString(SEPARATOR.toString())
    fun decode(value: String) = value.split(SEPARATOR).filter { it.isNotEmpty() }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class OrganisationDataModule {
    @Binds
    abstract fun starredRepository(impl: StarredRepositoryImpl): StarredRepository

    @Binds
    abstract fun scheduledRepository(impl: ScheduledRepositoryImpl): ScheduledRepository
}
