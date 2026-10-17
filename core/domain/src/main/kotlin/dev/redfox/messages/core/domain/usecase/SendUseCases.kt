package dev.redfox.messages.core.domain.usecase

import dev.redfox.messages.core.domain.repository.ContactsRepository
import dev.redfox.messages.core.domain.repository.MessagesRepository
import dev.redfox.messages.core.domain.repository.SimRepository
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageBox
import dev.redfox.messages.core.model.MessageKind
import dev.redfox.messages.core.model.SimCard
import javax.inject.Inject

class SendSmsUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    /** Returns false (and sends nothing) for a blank message or no recipients. */
    suspend operator fun invoke(addresses: List<String>, body: String, subscriptionId: Int): Boolean {
        val recipients = addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (recipients.isEmpty() || body.isBlank()) return false
        repository.sendSms(recipients, body.trimEnd(), subscriptionId)
        return true
    }
}

class RetryMessageUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    /** Only failed SMS can be retried; MMS sending isn't supported yet. */
    suspend operator fun invoke(message: Message) {
        if (message.kind != MessageKind.Sms || message.box != MessageBox.Failed) return
        repository.retry(message)
    }
}

class MarkThreadReadUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    suspend operator fun invoke(threadId: Long) = repository.markRead(threadId)
}

class ResolveThreadUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    suspend operator fun invoke(addresses: List<String>): Long = repository.threadIdFor(addresses)
}

/** Active SIMs, the system default SMS SIM first. */
class GetSimsUseCase @Inject constructor(
    private val sims: SimRepository,
) {
    data class Result(val sims: List<SimCard>, val defaultSubscriptionId: Int?)

    suspend operator fun invoke(): Result {
        val active = sims.activeSims().sortedBy { it.slotIndex }
        val default = sims.defaultSmsSubscriptionId()?.takeIf { id -> active.any { it.subscriptionId == id } }
        return Result(active, default)
    }
}

class SearchContactsUseCase @Inject constructor(
    private val contacts: ContactsRepository,
) {
    suspend operator fun invoke(query: String, limit: Int = 60): List<Contact> = contacts.search(query.trim(), limit)
}
