package dev.redfox.messages.core.telephony.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.mms.pdu.NotificationInd
import dev.redfox.messages.core.mms.pdu.PduParser
import dev.redfox.messages.core.telephony.mms.MmsStore
import dev.redfox.messages.core.telephony.mms.MmsTransport
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * `WAP_PUSH_DELIVER`: the carrier says an MMS is waiting on its server (M-Notification.ind).
 * Stores it as "downloading" (so it's never lost, even if the download fails) and fetches it
 * straight away. Delivery and read reports are ignored for now.
 */
@AndroidEntryPoint
class MmsWapPushReceiver : BroadcastReceiver() {

    @Inject lateinit var store: MmsStore
    @Inject lateinit var transport: MmsTransport
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val data = intent.getByteArrayExtra("data") ?: return
        val subId = intent.getIntExtra(SubscriptionManager.EXTRA_SUBSCRIPTION_INDEX, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val pending = goAsync()
        scope.launch(io) {
            try {
                val ind = runCatching { PduParser(data).parse() }.getOrNull() as? NotificationInd ?: return@launch
                val location = ind.contentLocation?.let { String(it) } ?: return@launch
                // Carriers sometimes push the same notification twice.
                if (store.findNotification(location) != null) return@launch
                val stored = store.storeNotification(ind, subId) ?: return@launch
                transport.download(stored.uri, location, subId)
            } catch (e: RuntimeException) {
                Log.e(TAG, "Couldn't handle MMS notification", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "MmsWapPushReceiver"
    }
}
