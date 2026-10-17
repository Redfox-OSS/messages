package dev.redfox.messages.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import dev.redfox.messages.core.telephony.IncomingMessageNotifier
import dev.redfox.messages.core.telephony.IncomingSms
import javax.inject.Inject

/**
 * Debug builds only: posts the notifications for a fake incoming SMS, without storing it, so
 * the OTP live update can be tried without waiting for a real code:
 *
 * ```
 * adb shell am broadcast -n dev.redfox.messages.debug/dev.redfox.messages.debug.DebugIncomingSmsReceiver \
 *   --es address VM-HDFCBK-S --es body "482913 is your OTP for login"
 * ```
 *
 * Guarded by the DUMP permission, which adb's shell has and ordinary apps don't.
 */
@AndroidEntryPoint
class DebugIncomingSmsReceiver : BroadcastReceiver() {

    @Inject lateinit var notifier: IncomingMessageNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val address = intent.getStringExtra("address") ?: "VM-TESTER-S"
        val body = intent.getStringExtra("body") ?: return
        notifier.notifySms(IncomingSms(address = address, body = body, uri = null))
    }
}
