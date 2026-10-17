package dev.redfox.messages.core.data.scheduled

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.data.RecipientCodec
import dev.redfox.messages.core.database.ScheduledDao
import dev.redfox.messages.core.telephony.IncomingMessageNotifier
import dev.redfox.messages.core.telephony.send.SmsSender
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exact alarms for scheduled messages. `USE_EXACT_ALARM` (granted at install for messaging
 * apps) lets `setExactAndAllowWhileIdle` fire on time even in Doze.
 */
@Singleton
class ScheduledAlarms @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val alarmManager get() = context.getSystemService(AlarmManager::class.java)

    fun set(id: Long, sendAt: Long) {
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, sendAt, pendingIntent(id))
    }

    fun cancel(id: Long) = alarmManager.cancel(pendingIntent(id))

    private fun pendingIntent(id: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        id.toInt(),
        Intent(context, ScheduledSendReceiver::class.java).setAction(ACTION_SEND).putExtra(EXTRA_ID, id),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    internal companion object {
        const val ACTION_SEND = "dev.redfox.messages.SEND_SCHEDULED"
        const val EXTRA_ID = "id"
    }
}

/** Sends a stored scheduled message now: hands it to the radio and removes it from the schedule. */
@Singleton
class ScheduledSender @Inject constructor(
    private val dao: ScheduledDao,
    private val sms: SmsSender,
    private val notifier: IncomingMessageNotifier,
) {
    /** Blocking (the radio hand-off); call from an IO dispatcher. */
    suspend fun send(id: Long) {
        val message = dao.get(id) ?: return
        try {
            RecipientCodec.decode(message.recipients).forEach { sms.send(it, message.body, message.subscriptionId) }
            // It's an ordinary outgoing SMS now, with its own Sending / Sent / Failed status.
            dao.delete(id)
        } catch (e: RuntimeException) {
            Log.e(TAG, "Scheduled send failed", e)
            dao.markFailed(id)
            notifier.notifySendFailed(message.title)
        }
    }

    private companion object {
        const val TAG = "ScheduledSender"
    }
}

/** Fires at a scheduled message's time. */
@AndroidEntryPoint
class ScheduledSendReceiver : BroadcastReceiver() {

    @Inject lateinit var sender: ScheduledSender
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ScheduledAlarms.ACTION_SEND) return
        val id = intent.getLongExtra(ScheduledAlarms.EXTRA_ID, -1).takeIf { it > 0 } ?: return
        val pending = goAsync()
        scope.launch(io) {
            try {
                sender.send(id)
            } finally {
                pending.finish()
            }
        }
    }
}

/**
 * Alarms don't survive a reboot (or an app update). Re-arms every pending message; any whose
 * time passed while the phone was off is sent straight away, as Google Messages does.
 */
@AndroidEntryPoint
class ScheduledRestoreReceiver : BroadcastReceiver() {

    @Inject lateinit var dao: ScheduledDao
    @Inject lateinit var alarms: ScheduledAlarms
    @Inject lateinit var sender: ScheduledSender
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        scope.launch(io) {
            try {
                val now = System.currentTimeMillis()
                dao.pending().forEach { message ->
                    if (message.sendAt <= now) sender.send(message.id) else alarms.set(message.id, message.sendAt)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
