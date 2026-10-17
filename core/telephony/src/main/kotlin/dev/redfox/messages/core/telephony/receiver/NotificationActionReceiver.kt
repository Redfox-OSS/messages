package dev.redfox.messages.core.telephony.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import dagger.hilt.android.AndroidEntryPoint
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.telephony.SmsProviderWriter
import dev.redfox.messages.core.telephony.send.SmsSender
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Reply and Mark read, straight from a message notification. */
@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var sender: SmsSender
    @Inject lateinit var writer: SmsProviderWriter
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val threadId = intent.getLongExtra(EXTRA_THREAD_ID, -1)
        val pending = goAsync()
        scope.launch(io) {
            try {
                when (intent.action) {
                    ACTION_REPLY -> {
                        val text = RemoteInput.getResultsFromIntent(intent)?.getCharSequence(KEY_REPLY_TEXT)
                            ?.toString()?.trimEnd()
                        val address = intent.getStringExtra(EXTRA_ADDRESS)
                        if (!text.isNullOrBlank() && address != null) {
                            sender.send(
                                address,
                                text,
                                intent.getIntExtra(EXTRA_SUB_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID),
                            )
                            // Replying means it's been read.
                            if (threadId > 0) writer.markThreadRead(threadId)
                        }
                    }
                    ACTION_MARK_READ -> if (threadId > 0) writer.markThreadRead(threadId)
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Notification action failed", e)
            } finally {
                NotificationManagerCompat.from(context).cancel(notificationId)
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "NotificationAction"
        const val KEY_REPLY_TEXT = "reply_text"
        private const val ACTION_REPLY = "dev.redfox.messages.REPLY"
        private const val ACTION_MARK_READ = "dev.redfox.messages.MARK_READ"
        private const val EXTRA_NOTIFICATION_ID = "notification_id"
        private const val EXTRA_THREAD_ID = "thread_id"
        private const val EXTRA_ADDRESS = "address"
        private const val EXTRA_SUB_ID = "sub_id"

        /** Mutable: the system adds the typed reply to the intent. Explicit component keeps it ours. */
        fun replyIntent(context: Context, notificationId: Int, address: String, threadId: Long, subId: Int): PendingIntent {
            val intent = Intent(ACTION_REPLY)
                .setClass(context, NotificationActionReceiver::class.java)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_ADDRESS, address)
                .putExtra(EXTRA_THREAD_ID, threadId)
                .putExtra(EXTRA_SUB_ID, subId)
            return PendingIntent.getBroadcast(
                context, notificationId, intent, PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        fun markReadIntent(context: Context, notificationId: Int, threadId: Long): PendingIntent {
            val intent = Intent(ACTION_MARK_READ)
                .setClass(context, NotificationActionReceiver::class.java)
                .putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                .putExtra(EXTRA_THREAD_ID, threadId)
            return PendingIntent.getBroadcast(
                context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
