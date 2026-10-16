package dev.redfox.messages.core.telephony.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.telephony.SmsProviderWriter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Quick replies sent from the incoming-call screen ("Can't talk now…"). */
@AndroidEntryPoint
class HeadlessSmsSendService : Service() {

    @Inject lateinit var writer: SmsProviderWriter
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(Intent.EXTRA_TEXT)
        val recipients = intent?.data?.schemeSpecificPart
            ?.split(';', ',')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        if (intent?.action != TelephonyManager.ACTION_RESPOND_VIA_MESSAGE || text.isNullOrBlank() || recipients.isEmpty()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        scope.launch(io) {
            try {
                val subId = SubscriptionManager.getDefaultSmsSubscriptionId()
                val smsManager = getSystemService(SmsManager::class.java).let {
                    if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) it.createForSubscriptionId(subId) else it
                }
                recipients.forEach { address ->
                    smsManager.sendMultipartTextMessage(address, null, smsManager.divideMessage(text), null, null)
                    writer.writeSent(address, text, subId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Quick reply failed", e)
            } finally {
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private companion object {
        const val TAG = "HeadlessSmsSendService"
    }
}
