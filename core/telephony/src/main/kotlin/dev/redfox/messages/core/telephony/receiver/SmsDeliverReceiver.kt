package dev.redfox.messages.core.telephony.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
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

/** `SMS_DELIVER`: only the default SMS app receives this, and it must store the message itself. */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {

    @Inject lateinit var writer: SmsProviderWriter
    @Inject lateinit var notifier: IncomingMessageNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val pending = goAsync()
        scope.launch(io) {
            try {
                writer.writeIncoming(intent)?.let(notifier::notifySms)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to store incoming SMS", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}
