package dev.redfox.messages.core.telephony

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.TextBasedSmsColumns
import android.telephony.SubscriptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A received SMS, reassembled from its PDUs. */
data class IncomingSms(
    val address: String,
    val body: String,
    val uri: Uri?,
    val threadId: Long = -1,
    val subscriptionId: Int = SubscriptionManager.INVALID_SUBSCRIPTION_ID,
)

/** What's needed to (re)send a stored outgoing SMS. */
data class OutgoingSms(val uri: Uri, val address: String, val body: String, val subscriptionId: Int)

/**
 * As the default SMS app we own writing to the Telephony provider: the OS delivers incoming
 * SMS only to us and stores nothing itself, and stores nothing we send either. Every received
 * and sent message passes through here. Blocking; call from an IO dispatcher.
 */
@Singleton
class SmsProviderWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val resolver get() = context.contentResolver

    /** Persists an `SMS_DELIVER` broadcast to the inbox. Returns null for an empty or malformed intent. */
    fun writeIncoming(intent: Intent): IncomingSms? {
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent)
            ?.filterNotNull()
            ?.takeIf { it.isNotEmpty() }
            ?: return null
        val first = parts.first()
        val address = first.displayOriginatingAddress ?: first.originatingAddress ?: return null
        val body = parts.joinToString(separator = "") { it.displayMessageBody.orEmpty() }
        val subId = intent.getIntExtra(
            SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX,
            SubscriptionManager.INVALID_SUBSCRIPTION_ID,
        )

        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.DATE_SENT, first.timestampMillis)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
            put(Telephony.Sms.PROTOCOL, first.protocolIdentifier)
            put(Telephony.Sms.REPLY_PATH_PRESENT, if (first.isReplyPathPresent) 1 else 0)
            first.serviceCenterAddress?.let { put(Telephony.Sms.SERVICE_CENTER, it) }
            if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
        }
        val uri = resolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        return IncomingSms(
            address = address,
            body = body,
            uri = uri,
            threadId = uri?.let(::threadIdOf) ?: -1,
            subscriptionId = subId,
        )
    }

    /**
     * Stores a message about to be handed to the radio, in the outbox, so it shows as
     * "Sending…" immediately. [SmsStatusReceiver][dev.redfox.messages.core.telephony.send.SmsStatusReceiver]
     * moves it to sent or failed.
     */
    fun writeOutgoing(address: String, body: String, subscriptionId: Int, deliveryReport: Boolean): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.THREAD_ID, threadIdFor(setOf(address)))
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            put(Telephony.Sms.STATUS, if (deliveryReport) Telephony.Sms.STATUS_PENDING else Telephony.Sms.STATUS_NONE)
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        return resolver.insert(Telephony.Sms.Outbox.CONTENT_URI, values)
    }

    /** Records a message already sent by someone else's code path (quick replies) in the sent box. */
    fun writeSent(address: String, body: String, subscriptionId: Int): Uri? {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, address)
            put(Telephony.Sms.BODY, body)
            put(Telephony.Sms.DATE, System.currentTimeMillis())
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
            if (subscriptionId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
                put(Telephony.Sms.SUBSCRIPTION_ID, subscriptionId)
            }
        }
        return resolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }

    fun readOutgoing(messageId: Long): OutgoingSms? {
        val uri = ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, messageId)
        return resolver.query(
            uri,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.SUBSCRIPTION_ID),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            val address = c.getString(0) ?: return@use null
            OutgoingSms(uri, address, c.getString(1).orEmpty(), c.getInt(2))
        }
    }

    /** Back into the outbox for a retry, dated now so it moves to the bottom of the thread. */
    fun markSending(uri: Uri, deliveryReport: Boolean) = update(uri) {
        put(Telephony.Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX)
        put(Telephony.Sms.DATE, System.currentTimeMillis())
        put(Telephony.Sms.ERROR_CODE, 0)
        put(Telephony.Sms.STATUS, if (deliveryReport) Telephony.Sms.STATUS_PENDING else Telephony.Sms.STATUS_NONE)
    }

    /** Sent, unless an earlier part already failed (part callbacks can arrive in any order). */
    fun markSent(uri: Uri) {
        resolver.update(
            uri,
            ContentValues().apply { put(Telephony.Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_SENT) },
            "${Telephony.Sms.TYPE} != ?",
            arrayOf(TextBasedSmsColumns.MESSAGE_TYPE_FAILED.toString()),
        )
    }

    fun markFailed(uri: Uri, errorCode: Int) = update(uri) {
        put(Telephony.Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_FAILED)
        put(Telephony.Sms.ERROR_CODE, errorCode)
        put(Telephony.Sms.STATUS, Telephony.Sms.STATUS_NONE)
    }

    /** One of [Telephony.Sms.STATUS_COMPLETE], [Telephony.Sms.STATUS_PENDING], [Telephony.Sms.STATUS_FAILED]. */
    fun markDelivery(uri: Uri, status: Int) = update(uri) { put(Telephony.Sms.STATUS, status) }

    /**
     * Messages still "sending" long after they were handed off never got a callback (the app
     * was killed mid-send, or the radio dropped it). Show them as failed so they can be retried.
     */
    fun failStaleOutbox(olderThanMillis: Long): Int = resolver.update(
        Telephony.Sms.CONTENT_URI,
        ContentValues().apply { put(Telephony.Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_FAILED) },
        "${Telephony.Sms.TYPE} IN (?, ?) AND ${Telephony.Sms.DATE} < ?",
        arrayOf(
            TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX.toString(),
            TextBasedSmsColumns.MESSAGE_TYPE_QUEUED.toString(),
            (System.currentTimeMillis() - olderThanMillis).toString(),
        ),
    )

    /** Marks a whole thread read and seen, SMS and MMS. */
    fun markThreadRead(threadId: Long) {
        val values = ContentValues().apply {
            put(Telephony.Sms.READ, 1)
            put(Telephony.Sms.SEEN, 1)
        }
        val unread = "${Telephony.Sms.THREAD_ID} = ? AND (${Telephony.Sms.READ} = 0 OR ${Telephony.Sms.SEEN} = 0)"
        val args = arrayOf(threadId.toString())
        resolver.update(Telephony.Sms.CONTENT_URI, values, unread, args)
        resolver.update(Telephony.Mms.CONTENT_URI, values, unread, args)
    }

    /** "Mark unread": the newest incoming SMS or MMS in the thread becomes unread again. */
    fun markLatestIncomingUnread(threadId: Long) {
        val values = ContentValues().apply { put(Telephony.Sms.READ, 0) }
        val sms = newestIncoming(Telephony.Sms.CONTENT_URI, Telephony.Sms.TYPE, TextBasedSmsColumns.MESSAGE_TYPE_INBOX, threadId)
        val mms = newestIncoming(Telephony.Mms.CONTENT_URI, Telephony.Mms.MESSAGE_BOX, Telephony.BaseMmsColumns.MESSAGE_BOX_INBOX, threadId)
        // MMS dates are seconds, SMS milliseconds.
        val target = listOfNotNull(sms, mms?.let { it.first to it.second * 1000 }).maxByOrNull { it.second } ?: return
        val uri = if (target === sms) Telephony.Sms.CONTENT_URI else Telephony.Mms.CONTENT_URI
        resolver.update(ContentUris.withAppendedId(uri, target.first), values, null, null)
    }

    private fun newestIncoming(uri: Uri, boxColumn: String, inbox: Int, threadId: Long): Pair<Long, Long>? =
        resolver.query(
            uri,
            arrayOf(Telephony.Sms._ID, Telephony.Sms.DATE),
            "${Telephony.Sms.THREAD_ID} = ? AND $boxColumn = ?",
            arrayOf(threadId.toString(), inbox.toString()),
            "${Telephony.Sms.DATE} DESC LIMIT 1",
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) to c.getLong(1) else null }

    /** Deletes a whole conversation (its SMS and MMS). */
    fun deleteThread(threadId: Long): Int =
        resolver.delete(ContentUris.withAppendedId(Telephony.Threads.CONTENT_URI, threadId), null, null)

    fun deleteSms(id: Long): Int = resolver.delete(ContentUris.withAppendedId(Telephony.Sms.CONTENT_URI, id), null, null)

    fun deleteMms(id: Long): Int = resolver.delete(ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, id), null, null)

    fun threadIdFor(addresses: Set<String>): Long = Telephony.Threads.getOrCreateThreadId(context, addresses)

    private fun threadIdOf(uri: Uri): Long? = resolver.query(uri, arrayOf(Telephony.Sms.THREAD_ID), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }

    private inline fun update(uri: Uri, values: ContentValues.() -> Unit) {
        resolver.update(uri, ContentValues().apply(values), null, null)
    }
}
