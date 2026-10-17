package dev.redfox.messages.core.telephony

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.domain.OtpExtractor
import dev.redfox.messages.core.telephony.receiver.NotificationActionReceiver
import dev.redfox.messages.core.telephony.receiver.OtpCopyReceiver
import javax.inject.Inject
import javax.inject.Singleton

/** Extras on the launch intent of a message notification, read by the app's activity. */
object MessageLaunchExtras {
    const val THREAD_ID = "dev.redfox.messages.extra.THREAD_ID"
    const val ADDRESS = "dev.redfox.messages.extra.ADDRESS"
}

/**
 * Message notifications: one per sender, with Reply and Mark read, opening the conversation
 * when tapped.
 *
 * A message with a one-time code also gets a short-lived **OTP live update**: on Android 16
 * it's a promoted ongoing notification, which OEM shells show in their pill / island UI
 * (Samsung Now Bar and status chip, OxygenOS/ColorOS Live Alerts, HyperOS Super Island…) with
 * the code as the chip text. Tapping it, or "Copy", copies the code. It disappears after
 * [OTP_VISIBLE_MILLIS]; the message notification stays in the shade, silently, with its own
 * copy action.
 */
@Singleton
class IncomingMessageNotifier @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun notifySms(sms: IncomingSms) {
        if (!canNotify()) return
        val otp = OtpExtractor.extract(sms.body)
        postMessage(sms, silent = otp != null, otp = otp)
        if (otp != null) postOtpLiveUpdate(otp, sender = SenderLabel.of(sms.address))
    }

    fun notifyMms() {
        if (!canNotify()) return
        ensureChannels()
        val notification = baseMessage(MMS_NOTIFICATION_ID, launchIntent(threadId = -1, address = null))
            .setContentTitle(context.getString(R.string.notification_mms_title))
            .setContentText(context.getString(R.string.notification_mms_text))
            .build()
        NotificationManagerCompat.from(context).notify(MMS_NOTIFICATION_ID, notification)
    }

    /** A message we sent was rejected by the network. */
    fun notifySendFailed(address: String) {
        if (!canNotify()) return
        ensureChannels()
        val id = FAILED_ID_BASE + address.hashCode()
        val notification = baseMessage(id, launchIntent(threadId = -1, address = address))
            .setContentTitle(context.getString(R.string.notification_send_failed_title))
            .setContentText(context.getString(R.string.notification_send_failed_text, address))
            .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Clears the message notifications for these senders (their conversation was opened or read). */
    fun cancelFor(addresses: Collection<String>) {
        val manager = NotificationManagerCompat.from(context)
        addresses.forEach { manager.cancel(notificationId(it)) }
    }

    private fun postMessage(sms: IncomingSms, silent: Boolean, otp: String?) {
        ensureChannels()
        val id = notificationId(sms.address)
        val builder = baseMessage(id, launchIntent(sms.threadId, sms.address))
            .setContentTitle(sms.address)
            .setContentText(sms.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(sms.body))
            .setSilent(silent)
        if (otp != null) {
            builder.addAction(
                R.drawable.ic_stat_copy,
                context.getString(R.string.notification_otp_copy, otp),
                OtpCopyReceiver.pendingIntent(context, otp, dismissId = null),
            )
        }
        // Business senders ("VM-HDFCBK") can't be texted back.
        if (sms.address.none { it.isLetter() }) builder.addAction(replyAction(id, sms))
        if (sms.threadId > 0) {
            builder.addAction(
                R.drawable.ic_stat_done,
                context.getString(R.string.notification_mark_read),
                NotificationActionReceiver.markReadIntent(context, id, sms.threadId),
            )
        }
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }

    private fun replyAction(notificationId: Int, sms: IncomingSms): NotificationCompat.Action {
        val input = RemoteInput.Builder(NotificationActionReceiver.KEY_REPLY_TEXT)
            .setLabel(context.getString(R.string.notification_reply_hint))
            .build()
        return NotificationCompat.Action.Builder(
            R.drawable.ic_stat_reply,
            context.getString(R.string.notification_reply),
            NotificationActionReceiver.replyIntent(context, notificationId, sms.address, sms.threadId, sms.subscriptionId),
        )
            .addRemoteInput(input)
            .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
            .setShowsUserInterface(false)
            .setAllowGeneratedReplies(true)
            .build()
    }

    private fun baseMessage(id: Int, contentIntent: PendingIntent?) =
        NotificationCompat.Builder(context, CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_stat_message)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)

    /** Opens the app on the conversation (by thread, or by address when the thread isn't known). */
    private fun launchIntent(threadId: Long, address: String?): PendingIntent? {
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return null
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (threadId > 0) launch.putExtra(MessageLaunchExtras.THREAD_ID, threadId)
        if (address != null) launch.putExtra(MessageLaunchExtras.ADDRESS, address)
        val requestCode = if (threadId > 0) threadId.toInt() else (address?.hashCode() ?: 0)
        return PendingIntent.getActivity(
            context,
            requestCode,
            launch,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun postOtpLiveUpdate(otp: String, sender: String) {
        val copy = OtpCopyReceiver.pendingIntent(context, otp, dismissId = OTP_NOTIFICATION_ID)
        // The lock screen shows that a code arrived, not the code itself.
        val public = NotificationCompat.Builder(context, CHANNEL_OTP)
            .setSmallIcon(R.drawable.ic_stat_otp)
            .setContentTitle(context.getString(R.string.notification_otp_public_title))
            .setContentText(sender)
            .build()
        val notification = NotificationCompat.Builder(context, CHANNEL_OTP)
            .setSmallIcon(R.drawable.ic_stat_otp)
            // Pills show the title next to the chip text, so the title names the sender and
            // the code appears once.
            .setContentTitle(context.getString(R.string.notification_otp_title, sender))
            .setContentText(otp)
            .setSubText(context.getString(R.string.notification_otp_subtext))
            // Live update: the chip / island shows the code itself.
            .setRequestPromotedOngoing(true)
            .setShortCriticalText(otp)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setPublicVersion(public)
            .setTimeoutAfter(OTP_VISIBLE_MILLIS)
            .setContentIntent(copy)
            .addAction(R.drawable.ic_stat_copy, context.getString(R.string.notification_otp_copy_short), copy)
            .build()
        logPromotion(notification)
        NotificationManagerCompat.from(context).notify(OTP_NOTIFICATION_ID, notification)
    }

    /** Debug aid: whether the system will actually treat the OTP notification as a live update. */
    private fun logPromotion(notification: Notification) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) return
        val manager = context.getSystemService(NotificationManager::class.java)
        Log.i(
            TAG,
            "OTP live update: promotable=${notification.hasPromotableCharacteristics()} " +
                "allowed=${manager.canPostPromotedNotifications()}",
        )
    }

    private fun canNotify() = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
        PackageManager.PERMISSION_GRANTED

    private fun ensureChannels() {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_MESSAGES) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_MESSAGES,
                    context.getString(R.string.channel_incoming_messages),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply { description = context.getString(R.string.channel_incoming_messages_description) }
            )
        }
        if (manager.getNotificationChannel(CHANNEL_OTP) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_OTP,
                    context.getString(R.string.channel_otp),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.channel_otp_description)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        }
    }

    internal companion object {
        const val TAG = "IncomingNotifier"
        const val CHANNEL_MESSAGES = "incoming_messages"
        const val CHANNEL_OTP = "otp_codes"
        const val MMS_NOTIFICATION_ID = 0x4D4D53
        const val OTP_NOTIFICATION_ID = 0x4F5450
        const val FAILED_ID_BASE = 0x464149
        const val OTP_VISIBLE_MILLIS = 5_000L

        fun notificationId(address: String) = address.hashCode()
    }
}

/** Short sender name for the OTP chip: "VM-HDFCBK-S" → "HDFCBK"; phone numbers unchanged. */
internal object SenderLabel {
    fun of(address: String): String {
        if (address.none { it.isLetter() }) return address
        val parts = address.split('-').filter { it.isNotBlank() }
        // Indian DLT headers are "XX-BRAND" with an optional "-S/-T/-P/-G" type suffix.
        return parts.filter { it.length > 2 }.maxByOrNull { it.length } ?: address
    }
}
