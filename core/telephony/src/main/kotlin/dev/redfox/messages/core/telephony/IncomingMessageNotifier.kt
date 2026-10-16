package dev.redfox.messages.core.telephony

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Minimal incoming-message notification so nothing goes unnoticed before the full
 * `:core:notifications` module (MessagingStyle, inline reply, per-chat channels) lands in M3.
 */
@Singleton
class IncomingMessageNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun notifySms(sms: IncomingSms) {
        post(id = sms.address.hashCode(), title = sms.address, text = sms.body)
    }

    fun notifyMms() {
        post(
            id = MMS_NOTIFICATION_ID,
            title = context.getString(R.string.notification_mms_title),
            text = context.getString(R.string.notification_mms_text),
        )
    }

    private fun post(id: Int, title: String, text: String) {
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        if (!granted) return
        ensureChannel()

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(context, id, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.channel_incoming_messages),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.channel_incoming_messages_description) }
        )
    }

    private companion object {
        const val CHANNEL_ID = "incoming_messages"
        const val MMS_NOTIFICATION_ID = 0x4D4D53
    }
}
