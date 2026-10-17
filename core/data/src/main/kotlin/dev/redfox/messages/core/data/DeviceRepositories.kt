package dev.redfox.messages.core.data

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.contacts.ContactSearch
import dev.redfox.messages.core.domain.repository.ContactsRepository
import dev.redfox.messages.core.domain.repository.SimRepository
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.SimCard
import dev.redfox.messages.core.telephony.SimCards
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject

internal class SimRepositoryImpl @Inject constructor(
    private val sims: SimCards,
    @param:Dispatcher(MessagesDispatcher.IO) private val io: CoroutineDispatcher,
) : SimRepository {
    override suspend fun activeSims(): List<SimCard> = withContext(io) { sims.active() }
    override suspend fun defaultSmsSubscriptionId(): Int? = withContext(io) { sims.defaultSmsSubscriptionId() }
}

internal class ContactsRepositoryImpl @Inject constructor(
    private val search: ContactSearch,
    @param:Dispatcher(MessagesDispatcher.IO) private val io: CoroutineDispatcher,
) : ContactsRepository {
    override suspend fun search(query: String, limit: Int): List<Contact> = withContext(io) {
        runCatching { search.search(query, limit) }.getOrDefault(emptyList())
    }
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DeviceDataModule {
    @Binds
    abstract fun simRepository(impl: SimRepositoryImpl): SimRepository

    @Binds
    abstract fun contactsRepository(impl: ContactsRepositoryImpl): ContactsRepository
}
