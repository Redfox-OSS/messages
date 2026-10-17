package dev.redfox.messages.core.domain.usecase

import dev.redfox.messages.core.domain.repository.ContactsRepository
import dev.redfox.messages.core.domain.repository.MessagesRepository
import dev.redfox.messages.core.domain.repository.SimRepository
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageBox
import dev.redfox.messages.core.model.OutgoingAttachment
import dev.redfox.messages.core.model.SimCard
import javax.inject.Inject

/**
 * Sends what the composer holds. Plain text to one person goes as SMS; anything with media,
 * or to several people (a group), goes as one MMS so everyone sees the same conversation.
 */
class SendSmsUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    /** Returns false (and sends nothing) when there's nothing to send or no recipients. */
    suspend operator fun invoke(
        addresses: List<String>,
        body: String,
        subscriptionId: Int,
        attachments: List<OutgoingAttachment> = emptyList(),
    ): Boolean {
        val recipients = addresses.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        if (recipients.isEmpty() || (body.isBlank() && attachments.isEmpty())) return false
        if (attachments.isEmpty() && recipients.size == 1) {
            repository.sendSms(recipients, body.trimEnd(), subscriptionId)
        } else {
            repository.sendMms(recipients, body.trim(), attachments, subscriptionId)
        }
        return true
    }
}

class RetryDownloadUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    suspend operator fun invoke(message: Message) {
        if (message.isPendingDownload) repository.retryDownload(message)
    }
}

class MaxMmsSizeUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    suspend operator fun invoke(subscriptionId: Int): Int = repository.maxMmsSize(subscriptionId)
}

class RetryMessageUseCase @Inject constructor(
    private val repository: MessagesRepository,
) {
    suspend operator fun invoke(message: Message) {
        if (message.box != MessageBox.Failed) return
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
