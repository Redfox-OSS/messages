package dev.redfox.messages.core.telephony.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import dagger.hilt.android.AndroidEntryPoint
import dev.redfox.messages.core.telephony.IncomingMessageNotifier
import javax.inject.Inject

/**
 * `WAP_PUSH_DELIVER` (MMS notification-ind). Downloading and storing MMS arrives in M4; until
 * then the user is told an MMS is waiting on the carrier's server.
 */
@AndroidEntryPoint
class MmsWapPushReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: IncomingMessageNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        notifier.notifyMms()
    }
}
