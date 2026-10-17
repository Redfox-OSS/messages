package dev.redfox.messages.core.telephony.receiver

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.PersistableBundle
import androidx.core.app.NotificationManagerCompat

/**
 * Copies a one-time code from a notification to the clipboard. The clip is marked sensitive,
 * so the system's copy preview and clipboard history hide it. The OTP live update is dismissed
 * once its code is copied.
 */
class OtpCopyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val code = intent.getStringExtra(EXTRA_CODE) ?: return
        val clip = ClipData.newPlainText(CLIP_LABEL, code).apply {
            description.extras = PersistableBundle().apply {
                putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
            }
        }
        context.getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
        val dismissId = intent.getIntExtra(EXTRA_DISMISS_ID, NO_ID)
        if (dismissId != NO_ID) NotificationManagerCompat.from(context).cancel(dismissId)
    }

    companion object {
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DISMISS_ID = "dismiss_id"
        private const val NO_ID = Int.MIN_VALUE
        private const val CLIP_LABEL = "OTP"

        fun pendingIntent(context: Context, code: String, dismissId: Int?): PendingIntent {
            val intent = Intent(context, OtpCopyReceiver::class.java)
                .putExtra(EXTRA_CODE, code)
                .putExtra(EXTRA_DISMISS_ID, dismissId ?: NO_ID)
            // One PendingIntent per code and target, so extras never go stale.
            val requestCode = (code + (dismissId ?: NO_ID)).hashCode()
            return PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
    }
}
