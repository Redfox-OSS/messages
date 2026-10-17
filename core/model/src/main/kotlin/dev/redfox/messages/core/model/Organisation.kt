package dev.redfox.messages.core.model

/**
 * A message the user starred. Kept as a snapshot (text, sender, conversation title), so the
 * Starred tab never re-reads the SMS store and survives the original being deleted.
 */
data class StarredMessage(
    /** [Message.key]: "Sms-123" / "Mms-45". */
    val key: String,
    val threadId: Long,
    val threadTitle: String,
    val body: String,
    /** Who sent it; null when it was the user's own message. */
    val sender: Contact?,
    val timestamp: Long,
    val starredAt: Long,
)

/** A message waiting to be sent at [sendAt] (epoch millis). */
data class ScheduledMessage(
    val id: Long,
    val threadId: Long,
    val recipients: List<String>,
    val title: String,
    val body: String,
    val subscriptionId: Int,
    val sendAt: Long,
    /** True when sending at the scheduled time failed; it stays listed so it can be retried. */
    val failed: Boolean = false,
)
