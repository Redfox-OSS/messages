package dev.redfox.messages.core.telephony.send

import android.app.PendingIntent
import android.content.Context
import android.net.Uri
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.telephony.SmsProviderWriter
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sends SMS through the radio. Each message is stored in the outbox first, then handed to
 * [SmsManager] with one "sent" and one "delivered" callback per part;
 * [SmsStatusReceiver] turns those into sent / failed / delivered on the stored row.
 * Blocking; call from an IO dispatcher.
 */
@Singleton
class SmsSender @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val writer: SmsProviderWriter,
) {
    /** Stores and sends one message. Returns the stored row, or null if it couldn't be stored. */
    fun send(address: String, body: String, subscriptionId: Int): Uri? {
        val subId = usableSubscription(subscriptionId)
        val uri = writer.writeOutgoing(address, body, subId, DELIVERY_REPORTS) ?: return null
        dispatch(uri, address, body, subId)
        return uri
    }

    /** Re-sends a stored message (normally a failed one) in place. */
    fun retry(messageId: Long) {
        val sms = writer.readOutgoing(messageId) ?: return
        writer.markSending(sms.uri, DELIVERY_REPORTS)
        dispatch(sms.uri, sms.address, sms.body, usableSubscription(sms.subscriptionId))
    }

    private fun dispatch(uri: Uri, address: String, body: String, subId: Int) {
        try {
            val manager = smsManager(subId)
            val parts = manager.divideMessage(body).takeIf { it.isNotEmpty() } ?: arrayListOf(body)
            val sent = ArrayList<PendingIntent>(parts.size)
            val delivered = ArrayList<PendingIntent?>(parts.size)
            parts.indices.forEach { part ->
                sent += SmsStatusReceiver.sentIntent(context, uri, part, parts.size)
                delivered += if (DELIVERY_REPORTS) SmsStatusReceiver.deliveredIntent(context, uri, part) else null
            }
            if (parts.size == 1) {
                manager.sendTextMessage(address, null, parts[0], sent[0], delivered[0])
            } else {
                manager.sendMultipartTextMessage(address, null, parts, sent, ArrayList(delivered))
            }
        } catch (e: RuntimeException) {
            // No service, invalid address, SIM removed… the radio never got it.
            Log.e(TAG, "Send failed before reaching the radio", e)
            writer.markFailed(uri, errorCode = -1)
        }
    }

    private fun smsManager(subId: Int): SmsManager {
        val manager = context.getSystemService(SmsManager::class.java)
        return if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) manager.createForSubscriptionId(subId) else manager
    }

    /** The requested SIM, else the system's default SMS SIM. */
    private fun usableSubscription(requested: Int): Int =
        if (requested != SubscriptionManager.INVALID_SUBSCRIPTION_ID && requested >= 0) requested
        else SubscriptionManager.getDefaultSmsSubscriptionId()

    private companion object {
        const val TAG = "SmsSender"

        /** Free on Indian carriers; gives the "Delivered" status. */
        const val DELIVERY_REPORTS = true
    }
}
