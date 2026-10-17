package dev.redfox.messages.core.telephony.mms

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.FileProvider
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.mms.ContentType
import dev.redfox.messages.core.mms.pdu.CharacterSets
import dev.redfox.messages.core.mms.pdu.EncodedStringValue
import dev.redfox.messages.core.mms.pdu.NotifyRespInd
import dev.redfox.messages.core.mms.pdu.PduBody
import dev.redfox.messages.core.mms.pdu.PduComposer
import dev.redfox.messages.core.mms.pdu.PduHeaders
import dev.redfox.messages.core.mms.pdu.PduParser
import dev.redfox.messages.core.mms.pdu.PduPart
import dev.redfox.messages.core.mms.pdu.RetrieveConf
import dev.redfox.messages.core.mms.pdu.SendConf
import dev.redfox.messages.core.mms.pdu.SendReq
import dev.redfox.messages.core.telephony.IncomingMessageNotifier
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Moves MMS over the carrier network with the platform's MMS service
 * ([SmsManager.downloadMultimediaMessage] / [SmsManager.sendMultimediaMessage]), which handles
 * the MMS APN and HTTP. PDUs travel as files in `cache/mms`, shared through [MmsFileProvider].
 */
@Singleton
class MmsTransport @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val store: MmsStore,
) {
    /** Asks the carrier's server for the message a notification points at. */
    fun download(notificationUri: Uri, contentLocation: String, subId: Int) {
        val file = newFile("dl")
        val intent = Intent(context, MmsDownloadedReceiver::class.java)
            .setAction(ACTION_DOWNLOADED)
            .setData(notificationUri)
            .putExtra(EXTRA_FILE, file.absolutePath)
            .putExtra(EXTRA_SUB_ID, subId)
        smsManager(subId).downloadMultimediaMessage(context, contentLocation, fileUri(file), headers(subId), broadcast(intent))
    }

    /** Sends a failed MMS again: the stored copy is replaced by a fresh one (it moves to the bottom). */
    fun resend(uri: Uri) {
        val message = store.readOutgoing(uri) ?: return
        store.delete(uri)
        val text = message.parts.firstOrNull { it.text != null }?.text
        send(message.threadId, message.recipients, text, message.parts.filter { it.text == null }, message.subId)
    }

    /** Retries a download that failed, from its stored notification. */
    fun retryDownload(notificationUri: Uri) {
        val (location, subId) = store.notificationDetails(notificationUri) ?: return
        download(notificationUri, location, subId)
    }

    /**
     * Stores and sends an MMS to every recipient at once (a group message when there are
     * several). Returns the stored message, or null if it couldn't be stored.
     */
    fun send(threadId: Long, recipients: List<String>, text: String?, attachments: List<OutgoingPart>, subId: Int): Uri? {
        val sub = usableSubscription(subId)
        val parts = buildList {
            if (!text.isNullOrBlank()) add(OutgoingPart(ContentType.TEXT_PLAIN, text.toByteArray(), "text_0.txt", text = text))
            addAll(attachments)
        }
        val uri = store.storeOutgoing(threadId, recipients, parts, sub) ?: return null
        try {
            val request = SendReq().apply {
                // Our own number when the phone knows it (as AOSP, Signal and QKSMS send); else
                // the request keeps "insert-address-token", which the carrier fills in.
                store.selfNumber(sub)?.let(::cleanRecipient)?.takeIf { it.isNotEmpty() }?.let { from = EncodedStringValue(it) }
                // The composer adds "/TYPE=PLMN" to phone numbers.
                recipients.map(::cleanRecipient).filter { it.isNotEmpty() }.forEach { addTo(EncodedStringValue(it)) }
                date = System.currentTimeMillis() / 1000
                setMessageClass(PduHeaders.MESSAGE_CLASS_PERSONAL_STR.toByteArray())
                expiry = EXPIRY_SECONDS
                priority = PduHeaders.PRIORITY_NORMAL
                deliveryReport = PduHeaders.VALUE_NO
                readReport = PduHeaders.VALUE_NO
                body = pduBody(parts)
                messageSize = parts.sumOf { it.data.size }.toLong()
            }
            val file = newFile("send").apply { writeBytes(PduComposer(context, request).make()) }
            dispatch(uri, file, sub, request.transactionId, attempt = 0)
        } catch (e: Exception) {
            // The message is already in the conversation: it shows as failed (tap to retry),
            // never also back in the composer, where it would be sent twice.
            Log.e(TAG, "MMS send failed before reaching the radio", e)
            store.markFailed(uri)
        }
        return uri
    }

    /**
     * Hands a composed send request to the platform. [attempt] counts automatic resends of the
     * same request (see [scheduleRetry]); the result arrives at [MmsSentReceiver].
     */
    private fun dispatch(uri: Uri, file: File, subId: Int, transactionId: ByteArray?, attempt: Int) {
        val intent = Intent(context, MmsSentReceiver::class.java)
            .setAction(ACTION_SENT)
            .setData(uri)
            .putExtra(EXTRA_FILE, file.absolutePath)
            .putExtra(EXTRA_SUB_ID, subId)
            .putExtra(EXTRA_TRANSACTION_ID, transactionId)
            .putExtra(EXTRA_ATTEMPT, attempt)
        smsManager(subId).sendMultimediaMessage(context, fileUri(file), null, headers(subId), broadcast(intent))
    }

    /**
     * Sends the same request again after [delayMillis], for a failure that was likely temporary
     * (no MMS connection yet, a dropped connection, a busy server). The message keeps showing
     * "Sending" meanwhile; [MmsRetryReceiver] fires even if the app was closed.
     */
    fun scheduleRetry(uri: Uri, file: File, subId: Int, transactionId: ByteArray?, attempt: Int, delayMillis: Long) {
        val intent = Intent(context, MmsRetryReceiver::class.java)
            .setAction(ACTION_RETRY)
            .setData(uri)
            .putExtra(EXTRA_FILE, file.absolutePath)
            .putExtra(EXTRA_SUB_ID, subId)
            .putExtra(EXTRA_TRANSACTION_ID, transactionId)
            .putExtra(EXTRA_ATTEMPT, attempt)
        val pending = PendingIntent.getBroadcast(
            context, uri.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        context.getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + delayMillis, pending)
    }

    /** An automatic resend is due: send the same request, unless the message was deleted meanwhile. */
    fun retry(uri: Uri, file: File, subId: Int, transactionId: ByteArray?, attempt: Int) {
        if (!store.exists(uri) || !file.exists()) {
            file.delete()
            return
        }
        try {
            dispatch(uri, file, subId, transactionId, attempt)
        } catch (e: Exception) {
            Log.e(TAG, "MMS resend failed before reaching the radio", e)
            file.delete()
            store.markFailed(uri)
        }
    }

    /**
     * Tells the carrier the message arrived (M-NotifyResp.ind, "retrieved"), so it isn't
     * pushed again. Best effort: many carriers don't need it.
     */
    fun acknowledge(transactionId: ByteArray, subId: Int) = runCatching {
        val ack = NotifyRespInd(PduHeaders.CURRENT_MMS_VERSION, transactionId, PduHeaders.STATUS_RETRIEVED)
        val file = newFile("ack").apply { writeBytes(PduComposer(context, ack).make()) }
        smsManager(subId).sendMultimediaMessage(context, fileUri(file), null, headers(subId), null)
    }.onFailure { Log.w(TAG, "Couldn't acknowledge MMS", it) }

    /** The carrier's maximum MMS size in bytes, to size photos and voice notes to fit. */
    fun maxMessageSize(subId: Int): Int =
        runCatching { smsManager(usableSubscription(subId)).carrierConfigValues.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE) }
            .getOrNull()?.takeIf { it > 0 } ?: DEFAULT_MAX_SIZE

    /** A SMIL layout first (older phones need it), then the parts in order. */
    private fun pduBody(parts: List<OutgoingPart>) = PduBody().apply {
        addPart(PduPart().apply {
            contentType = ContentType.APP_SMIL.toByteArray()
            contentId = "<smil>".toByteArray()
            contentLocation = "smil.xml".toByteArray()
            data = smil(parts).toByteArray()
        })
        parts.forEach { part ->
            addPart(PduPart().apply {
                contentType = part.contentType.toByteArray()
                name = part.name.toByteArray()
                filename = part.name.toByteArray()
                contentId = "<${part.name}>".toByteArray()
                contentLocation = part.name.toByteArray()
                if (part.text != null) charset = CharacterSets.UTF_8
                data = part.data
            })
        }
    }

    private fun smil(parts: List<OutgoingPart>): String {
        val body = parts.joinToString("") { part ->
            val tag = when {
                part.contentType.startsWith("image/") -> "img"
                part.contentType.startsWith("audio/") -> "audio"
                part.contentType.startsWith("video/") -> "video"
                part.contentType.startsWith("text/") -> "text"
                else -> "ref"
            }
            val region = if (tag == "text") " region=\"Text\"" else if (tag == "img" || tag == "video") " region=\"Image\"" else ""
            "<par dur=\"5000ms\"><$tag src=\"${part.name}\"$region/></par>"
        }
        return "<smil><head><layout><root-layout/>" +
            "<region id=\"Image\" fit=\"meet\" top=\"0\" left=\"0\" height=\"80%\" width=\"100%\"/>" +
            "<region id=\"Text\" top=\"80%\" left=\"0\" height=\"20%\" width=\"100%\"/>" +
            "</layout></head><body>$body</body></smil>"
    }

    private fun newFile(prefix: String): File =
        File(context.cacheDir, "mms").apply { mkdirs() }.let { File.createTempFile(prefix, ".pdu", it) }

    private fun fileUri(file: File): Uri = FileProvider.getUriForFile(context, MmsFileProvider.authority(context), file)

    private fun broadcast(intent: Intent): PendingIntent = PendingIntent.getBroadcast(
        context,
        intent.data.hashCode(),
        intent,
        // Mutable: the MMS service adds the result PDU to the intent. The explicit component keeps it ours.
        PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /**
     * The phone-identifying headers the carrier's MMS server expects, where the carrier config
     * leaves them empty (see [MmsHeaders]).
     */
    private fun headers(subId: Int): android.os.Bundle? {
        val config = runCatching { smsManager(subId).carrierConfigValues }.getOrNull()
        val headers = MmsHeaders.overrides(
            carrierUserAgent = config?.getString(SmsManager.MMS_CONFIG_USER_AGENT),
            carrierUaProfUrl = config?.getString(SmsManager.MMS_CONFIG_UA_PROF_URL),
        )
        Log.w(TAG, "MMS headers: UA=${headers.userAgent ?: "(carrier)"} profile=${headers.uaProfUrl ?: "(carrier)"}")
        return headers.toBundle()
    }

    private fun smsManager(subId: Int): SmsManager {
        val manager = context.getSystemService(SmsManager::class.java)
        return if (subId != SubscriptionManager.INVALID_SUBSCRIPTION_ID && subId >= 0) manager.createForSubscriptionId(subId) else manager
    }

    private fun usableSubscription(requested: Int): Int =
        if (requested != SubscriptionManager.INVALID_SUBSCRIPTION_ID && requested >= 0) requested
        else SubscriptionManager.getDefaultSmsSubscriptionId()

    internal companion object {
        const val TAG = "MmsTransport"
        const val ACTION_DOWNLOADED = "dev.redfox.messages.MMS_DOWNLOADED"
        const val ACTION_SENT = "dev.redfox.messages.MMS_SENT"
        const val EXTRA_FILE = "file"
        const val EXTRA_SUB_ID = "sub_id"
        const val ACTION_RETRY = "dev.redfox.messages.MMS_RETRY"
        const val EXTRA_TRANSACTION_ID = "transaction_id"
        const val EXTRA_ATTEMPT = "attempt"
        const val EXPIRY_SECONDS = 7L * 24 * 60 * 60
        /** Indian carriers commonly allow 300 KB; most allow more. */
        const val DEFAULT_MAX_SIZE = 300 * 1024
    }
}

/** Only our MMS PDU files, only for the platform MMS service (granted per request). */
class MmsFileProvider : FileProvider() {
    companion object {
        fun authority(context: Context) = "${context.packageName}.mmsfiles"
    }
}

/** A message the carrier pushed has been downloaded (or not). */
@AndroidEntryPoint
class MmsDownloadedReceiver : BroadcastReceiver() {

    @Inject lateinit var store: MmsStore
    @Inject lateinit var transport: MmsTransport
    @Inject lateinit var notifier: IncomingMessageNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        val notificationUri = intent.data ?: return
        val file = intent.getStringExtra(MmsTransport.EXTRA_FILE)?.let(::File)
        val subId = intent.getIntExtra(MmsTransport.EXTRA_SUB_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val result = resultCode
        val pending = goAsync()
        scope.launch(io) {
            try {
                val bytes = file?.takeIf { result == Activity.RESULT_OK && it.length() > 0 }?.readBytes()
                val conf = bytes?.let { runCatching { PduParser(it).parse() }.getOrNull() } as? RetrieveConf
                if (conf == null) {
                    val http = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0)
                    Log.w(MmsTransport.TAG, "MMS download failed: result=$result http=$http")
                    store.markDownloadFailed(notificationUri)
                    return@launch
                }
                // The reply to the carrier carries the notification's transaction id: the
                // downloaded message usually has none, so it must be read before the
                // notification is deleted. Without the reply some carriers send it again, or
                // tell the sender it never arrived.
                val transactionId = store.transactionId(notificationUri) ?: conf.transactionId
                val stored = store.storeRetrieved(conf, subId) ?: return@launch
                store.delete(notificationUri)
                notifier.notifyMms(stored)
                if (transactionId != null) transport.acknowledge(transactionId, subId)
                else Log.w(MmsTransport.TAG, "No transaction id to acknowledge the MMS with")
            } catch (e: RuntimeException) {
                Log.e(MmsTransport.TAG, "Couldn't store downloaded MMS", e)
                store.markDownloadFailed(notificationUri)
            } finally {
                file?.delete()
                pending.finish()
            }
        }
    }
}

/** The carrier accepted (or rejected) a message we sent. */
@AndroidEntryPoint
class MmsSentReceiver : BroadcastReceiver() {

    @Inject lateinit var store: MmsStore
    @Inject lateinit var transport: MmsTransport
    @Inject lateinit var notifier: IncomingMessageNotifier
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.data ?: return
        val result = resultCode
        val pending = goAsync()
        scope.launch(io) {
            val file = intent.getStringExtra(MmsTransport.EXTRA_FILE)?.let(::File)
            var keepFile = false
            try {
                val conf = intent.getByteArrayExtra(SmsManager.EXTRA_MMS_DATA)
                    ?.let { runCatching { PduParser(it).parse() }.getOrNull() } as? SendConf
                val http = intent.getIntExtra(SmsManager.EXTRA_MMS_HTTP_STATUS, 0)
                val subId = intent.getIntExtra(MmsTransport.EXTRA_SUB_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
                val sentId = intent.getByteArrayExtra(MmsTransport.EXTRA_TRANSACTION_ID)
                val attempt = intent.getIntExtra(MmsTransport.EXTRA_ATTEMPT, 0)
                // A confirmation for another message is no confirmation (when both carry an id).
                val confId = conf?.transactionId
                val matches = sentId == null || confId == null || sentId.contentEquals(confId)
                val outcome = sendOutcome(result == Activity.RESULT_OK, result, http, conf?.responseStatus, matches)
                Log.w(
                    MmsTransport.TAG,
                    "MMS send result: result=$result http=$http status=${conf?.responseStatus} " +
                        "matches=$matches attempt=$attempt -> $outcome",
                )
                when {
                    outcome == SendOutcome.Sent -> store.markSent(uri, conf?.messageId?.let { String(it) })
                    outcome == SendOutcome.Retry && attempt < SEND_RETRY_DELAYS_MILLIS.size && file?.exists() == true -> {
                        keepFile = true
                        transport.scheduleRetry(uri, file, subId, sentId, attempt + 1, SEND_RETRY_DELAYS_MILLIS[attempt])
                    }
                    else -> {
                        store.markFailed(uri)
                        val (title, threadId) = store.describe(uri)
                        notifier.notifySendFailed(title, threadId)
                    }
                }
            } catch (e: RuntimeException) {
                Log.e(MmsTransport.TAG, "Couldn't record MMS send result", e)
            } finally {
                if (!keepFile) file?.delete()
                pending.finish()
            }
        }
    }
}

/** An automatic resend of an MMS is due (scheduled by [MmsTransport.scheduleRetry]). */
@AndroidEntryPoint
class MmsRetryReceiver : BroadcastReceiver() {

    @Inject lateinit var transport: MmsTransport
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    @Inject @Dispatcher(MessagesDispatcher.IO) lateinit var io: CoroutineDispatcher

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != MmsTransport.ACTION_RETRY) return
        val uri = intent.data ?: return
        val file = intent.getStringExtra(MmsTransport.EXTRA_FILE)?.let(::File) ?: return
        val subId = intent.getIntExtra(MmsTransport.EXTRA_SUB_ID, SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val transactionId = intent.getByteArrayExtra(MmsTransport.EXTRA_TRANSACTION_ID)
        val attempt = intent.getIntExtra(MmsTransport.EXTRA_ATTEMPT, 1)
        val pending = goAsync()
        scope.launch(io) {
            try {
                Log.w(MmsTransport.TAG, "Resending MMS $uri (attempt $attempt)")
                transport.retry(uri, file, subId, transactionId, attempt)
            } finally {
                pending.finish()
            }
        }
    }
}
