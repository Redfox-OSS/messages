package dev.redfox.messages.core.telephony

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SubscriptionManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** A received SMS, reassembled from its PDUs. */
data class IncomingSms(val address: String, val body: String, val uri: Uri?)

/**
 * As the default SMS app we own writing to the Telephony provider: the OS delivers incoming
 * SMS only to us and stores nothing itself. Every received and sent message must pass through here.
 */
@Singleton
class SmsProviderWriter @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
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
        val uri = context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
        return IncomingSms(address = address, body = body, uri = uri)
    }

    /** Records a message we handed to `SmsManager` in the sent box. */
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
        return context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
    }
}
