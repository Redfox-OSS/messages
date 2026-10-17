package dev.redfox.messages.core.telephony.send

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.telephony.IncomingMessageNotifier
import dev.redfox.messages.core.telephony.SmsProviderWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The radio's callbacks for messages [SmsSender] handed off: "sent" once per part (the
 * network accepted it, or not) and "delivered" once per part (the recipient's phone got it).
 */
@AndroidEntryPoint
class SmsStatusReceiver : BroadcastReceiver() {

    @Inject lateinit var writer: SmsProviderWriter
    @Inject lateinit var notifier: IncomingMessageNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data?.let { messageUri(it) } ?: return
        val result = resultCode
        val pending = goAsync()
        scope.launch(io) {
            try {
                when (intent.action) {
                    ACTION_SENT -> onSent(uri, intent, result)
                    ACTION_DELIVERED -> onDelivered(uri, intent)
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Couldn't record send status", e)
            } finally {
                pending.finish()
            }
        }
    }

    private fun onSent(uri: Uri, intent: Intent, result: Int) {
        if (result == Activity.RESULT_OK) {
            val part = intent.getIntExtra(EXTRA_PART, 0)
            val count = intent.getIntExtra(EXTRA_PART_COUNT, 1)
            if (part == count - 1) writer.markSent(uri)
        } else {
            Log.w(TAG, "Send failed: result=$result")
            writer.markFailed(uri, errorCode = result)
            writer.readOutgoing(ContentUris.parseId(uri))?.let { notifier.notifySendFailed(it.address) }
        }
    }

    private fun onDelivered(uri: Uri, intent: Intent) {
        val pdu = intent.getByteArrayExtra("pdu") ?: return
        val format = intent.getStringExtra("format")
        val status = SmsMessage.createFromPdu(pdu, format)?.status ?: return
        // TP-Status (3GPP TS 23.040): 0x00–0x1F done, 0x20–0x3F still trying, 0x40+ gave up.
        val mapped = when {
            status < 0 -> return
            status < 0x20 -> Telephony.Sms.STATUS_COMPLETE
            status < 0x40 -> Telephony.Sms.STATUS_PENDING
            else -> Telephony.Sms.STATUS_FAILED
        }
        writer.markDelivery(uri, mapped)
    }

    companion object {
        private const val TAG = "SmsStatusReceiver"
        private const val ACTION_SENT = "dev.redfox.messages.SMS_SENT"
        private const val ACTION_DELIVERED = "dev.redfox.messages.SMS_DELIVERED"
        private const val EXTRA_PART = "part"
        private const val EXTRA_PART_COUNT = "part_count"
        private const val PART_PARAM = "part"

        fun sentIntent(context: Context, message: Uri, part: Int, partCount: Int): PendingIntent =
            pendingIntent(context, ACTION_SENT, message, part) {
                putExtra(EXTRA_PART, part)
                putExtra(EXTRA_PART_COUNT, partCount)
            }

        fun deliveredIntent(context: Context, message: Uri, part: Int): PendingIntent =
            pendingIntent(context, ACTION_DELIVERED, message, part) {}

        /**
         * One PendingIntent per message part: the part is in the data URI so they never
         * collapse into each other. Mutable because the system fills in the delivery PDU; the
         * explicit component keeps it ours.
         */
        private inline fun pendingIntent(
            context: Context,
            action: String,
            message: Uri,
            part: Int,
            extras: Intent.() -> Unit,
        ): PendingIntent {
            val intent = Intent(action, message.buildUpon().appendQueryParameter(PART_PARAM, part.toString()).build())
                .setClass(context, SmsStatusReceiver::class.java)
                .apply(extras)
            return PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }

        private fun messageUri(data: Uri): Uri = data.buildUpon().clearQuery().build()
    }
}
