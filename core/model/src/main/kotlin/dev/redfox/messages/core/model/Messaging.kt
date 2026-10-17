package dev.redfox.messages.core.model

/** Someone on the other end of a conversation. [name] is null when they aren't in contacts. */
data class Contact(
    val address: String,
    val name: String? = null,
    val photoUri: String? = null,
    /** Address formatted for display (e.g. "+91 98765 43210"); alphanumeric senders unchanged. */
    val formattedAddress: String = address,
) {
    val displayName: String get() = name ?: formattedAddress

    /** Bank / service senders like "VM-HDFCBK" rather than a phone number. */
    val isAlphanumericSender: Boolean get() = address.any { it.isLetter() }
}

/** Inbox categories, as in Google Messages (India). */
enum class MessageCategory { Personal, Transactions, Otp, Offers }

data class Conversation(
    val threadId: Long,
    val participants: List<Contact>,
    val snippet: String,
    /** Epoch millis of the latest message. */
    val timestamp: Long,
    val unreadCount: Int,
    val messageCount: Int,
    val hasAttachment: Boolean,
    val category: MessageCategory,
) {
    val isGroup: Boolean get() = participants.size > 1
    val title: String get() = participants.joinToString { it.displayName }.ifEmpty { "Unknown" }
    val isUnread: Boolean get() = unreadCount > 0
}

enum class MessageKind { Sms, Mms }

/** Which mailbox a message is in, normalised across SMS `type` and MMS `msg_box`. */
enum class MessageBox { Inbox, Sent, Outbox, Draft, Failed, Queued;
    val isOutgoing: Boolean get() = this != Inbox
}

data class Attachment(
    val uri: String,
    val mimeType: String,
    val name: String? = null,
) {
    val isImage: Boolean get() = mimeType.startsWith("image/")
    val isVideo: Boolean get() = mimeType.startsWith("video/")
    val isAudio: Boolean get() = mimeType.startsWith("audio/")
}

data class Message(
    val id: Long,
    val threadId: Long,
    val kind: MessageKind,
    val box: MessageBox,
    /** For incoming messages, who sent it; for outgoing, null. */
    val sender: Contact?,
    val body: String,
    /** Epoch millis. */
    val timestamp: Long,
    val isRead: Boolean,
    val subscriptionId: Int,
    val attachments: List<Attachment> = emptyList(),
    val subject: String? = null,
    /** True for an MMS whose content hasn't been downloaded from the carrier yet. */
    val isPendingDownload: Boolean = false,
    /** A business chatbot card (RCS Business Messaging) stored alongside SMS/MMS, already parsed. */
    val rich: RichContent? = null,
    /** Delivery report for an outgoing SMS; [DeliveryStatus.None] when none was requested. */
    val deliveryStatus: DeliveryStatus = DeliveryStatus.None,
) {
    /** SMS and MMS ids come from different tables and can collide. */
    val key: String get() = "${kind.name}-$id"
    val isOutgoing: Boolean get() = box.isOutgoing
}

/** The recipient's handset report for an outgoing SMS. */
enum class DeliveryStatus { None, Pending, Delivered, Failed }

/** An active SIM that can send messages. */
data class SimCard(
    val subscriptionId: Int,
    /** The carrier or user-given name ("Jio", "BSNL Mobile"). */
    val label: String,
    /** 0-based physical slot, for "SIM 1" / "SIM 2" badges. */
    val slotIndex: Int,
)

/** Progress of indexing the system SMS store into the app's inbox index. */
data class SyncStatus(
    val isSyncing: Boolean = false,
    /** False until the first full pass finishes — an empty inbox before that means "loading". */
    val hasCompletedOnce: Boolean = false,
)
