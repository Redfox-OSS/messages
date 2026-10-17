package dev.redfox.messages.core.telephony.mms

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.BaseMmsColumns
import android.telephony.PhoneNumberUtils
import android.telephony.SubscriptionManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.mms.ContentType
import dev.redfox.messages.core.mms.pdu.CharacterSets
import dev.redfox.messages.core.mms.pdu.EncodedStringValue
import dev.redfox.messages.core.mms.pdu.NotificationInd
import dev.redfox.messages.core.mms.pdu.PduHeaders
import dev.redfox.messages.core.mms.pdu.PduPart
import dev.redfox.messages.core.mms.pdu.RetrieveConf
import javax.inject.Inject
import javax.inject.Singleton

/** A stored MMS, with what a notification needs. */
data class StoredMms(
    val uri: Uri,
    val threadId: Long,
    val from: String?,
    /** The message's text, if any. */
    val text: String?,
    /** Mime types of its media parts (image/…, audio/…), excluding text and SMIL. */
    val mediaTypes: List<String>,
)

/** One part of a message we're about to send or store. */
data class OutgoingPart(val contentType: String, val data: ByteArray, val name: String, val text: String? = null)

/**
 * Writes MMS into the system Telephony provider (`content://mms`), the shared store every SMS
 * app reads: one `pdu` row, its addresses and its parts. Replaces AOSP's PduPersister, which
 * leans on framework internals. Blocking; call from an IO dispatcher.
 */
@Singleton
class MmsStore @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    private val resolver get() = context.contentResolver

    /**
     * Stores an MMS notification (M-Notification.ind): the message waiting on the carrier's
     * server. It shows as "downloading" until [storeRetrieved] replaces it.
     */
    fun storeNotification(ind: NotificationInd, subId: Int): StoredMms? {
        val from = ind.from?.string?.let(::cleanAddress)
        val threadId = Telephony.Threads.getOrCreateThreadId(context, setOfNotNull(from).ifEmpty { setOf("unknown") })
        val values = ContentValues().apply {
            put(BaseMmsColumns.THREAD_ID, threadId)
            put(BaseMmsColumns.MESSAGE_BOX, BaseMmsColumns.MESSAGE_BOX_INBOX)
            put(BaseMmsColumns.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND)
            put(BaseMmsColumns.DATE, System.currentTimeMillis() / 1000)
            put(BaseMmsColumns.READ, 0)
            put(BaseMmsColumns.SEEN, 0)
            put(BaseMmsColumns.CONTENT_LOCATION, ind.contentLocation?.let { String(it) })
            put(BaseMmsColumns.TRANSACTION_ID, ind.transactionId?.let { String(it) })
            put(BaseMmsColumns.MESSAGE_SIZE, ind.messageSize)
            put(BaseMmsColumns.EXPIRY, ind.expiry)
            put(BaseMmsColumns.MESSAGE_CLASS, ind.messageClass?.let { String(it) })
            put(BaseMmsColumns.MMS_VERSION, ind.mmsVersion)
            ind.subject?.let { putSubject(it) }
            if (subId >= 0) put(BaseMmsColumns.SUBSCRIPTION_ID, subId)
        }
        val uri = resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
        from?.let { insertAddress(uri, it, PduHeaders.FROM) }
        return StoredMms(uri, threadId, from, text = null, mediaTypes = emptyList())
    }

    /** Whether a notification for this download is already stored (carriers sometimes push twice). */
    fun findNotification(contentLocation: String): Uri? = resolver.query(
        Telephony.Mms.CONTENT_URI,
        arrayOf(BaseMmsColumns._ID),
        "${BaseMmsColumns.CONTENT_LOCATION} = ? AND ${BaseMmsColumns.MESSAGE_TYPE} = ?",
        arrayOf(contentLocation, PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND.toString()),
        null,
    )?.use { c -> if (c.moveToFirst()) ContentUris.withAppendedId(Telephony.Mms.CONTENT_URI, c.getLong(0)) else null }

    /**
     * Stores a downloaded MMS (M-Retrieve.conf) in the inbox, in the right conversation: the
     * sender plus everyone else it went to, minus this phone, so group messages land in the
     * group thread.
     */
    fun storeRetrieved(conf: RetrieveConf, subId: Int): StoredMms? {
        val from = conf.from?.string?.let(::cleanAddress)
        val others = (conf.to?.toList().orEmpty() + conf.cc?.toList().orEmpty()).mapNotNull { value -> value?.string?.let(::cleanAddress) }
        val self = selfNumbers(subId)
        val othersMinusSelf = others.filterNot { address -> self.any { PhoneNumberUtils.areSamePhoneNumber(address, it, countryIso()) } }
        // A one-to-one MMS lists only this phone in "to"; if our number is unknown, drop that one.
        val recipients = (listOfNotNull(from) + if (self.isEmpty() && others.size == 1) emptyList() else othersMinusSelf)
            .distinctBy { it.filter(Char::isLetterOrDigit).takeLast(10) }
        val threadId = Telephony.Threads.getOrCreateThreadId(context, recipients.toSet().ifEmpty { setOf("unknown") })

        val values = ContentValues().apply {
            put(BaseMmsColumns.THREAD_ID, threadId)
            put(BaseMmsColumns.MESSAGE_BOX, BaseMmsColumns.MESSAGE_BOX_INBOX)
            put(BaseMmsColumns.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF)
            put(BaseMmsColumns.DATE, System.currentTimeMillis() / 1000)
            put(BaseMmsColumns.DATE_SENT, conf.date)
            put(BaseMmsColumns.READ, 0)
            put(BaseMmsColumns.SEEN, 0)
            put(BaseMmsColumns.MESSAGE_ID, conf.messageId?.let { String(it) })
            put(BaseMmsColumns.TRANSACTION_ID, conf.transactionId?.let { String(it) })
            put(BaseMmsColumns.CONTENT_TYPE, conf.contentType?.let { String(it) })
            put(BaseMmsColumns.MESSAGE_CLASS, conf.messageClass?.let { String(it) })
            put(BaseMmsColumns.MMS_VERSION, conf.mmsVersion)
            put(BaseMmsColumns.PRIORITY, conf.priority)
            conf.subject?.let { putSubject(it) }
            if (subId >= 0) put(BaseMmsColumns.SUBSCRIPTION_ID, subId)
        }
        val uri = resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
        from?.let { insertAddress(uri, it, PduHeaders.FROM) }
        others.forEach { insertAddress(uri, it, PduHeaders.TO) }

        var text: String? = null
        val media = ArrayList<String>()
        val body = conf.body
        for (i in 0 until (body?.partsNum ?: 0)) {
            val part = body.getPart(i)
            val type = part.contentType?.let { String(it) }?.lowercase() ?: continue
            val partText = insertPart(uri, i, part, type)
            when {
                type == ContentType.TEXT_PLAIN -> text = listOfNotNull(text, partText).joinToString("\n").ifEmpty { null }
                type != ContentType.APP_SMIL -> media += type
            }
        }
        return StoredMms(uri, threadId, from, text, media)
    }

    /**
     * Stores a message we're sending, in the outbox, so it shows as "Sending…" straight away.
     * [MmsSentReceiver][dev.redfox.messages.core.telephony.mms.MmsSentReceiver] moves it on.
     */
    fun storeOutgoing(threadId: Long, recipients: List<String>, parts: List<OutgoingPart>, subId: Int): Uri? {
        val values = ContentValues().apply {
            put(BaseMmsColumns.THREAD_ID, threadId)
            put(BaseMmsColumns.MESSAGE_BOX, BaseMmsColumns.MESSAGE_BOX_OUTBOX)
            put(BaseMmsColumns.MESSAGE_TYPE, PduHeaders.MESSAGE_TYPE_SEND_REQ)
            put(BaseMmsColumns.DATE, System.currentTimeMillis() / 1000)
            put(BaseMmsColumns.READ, 1)
            put(BaseMmsColumns.SEEN, 1)
            put(BaseMmsColumns.CONTENT_TYPE, ContentType.MULTIPART_RELATED)
            put(BaseMmsColumns.MMS_VERSION, PduHeaders.CURRENT_MMS_VERSION)
            put(BaseMmsColumns.TEXT_ONLY, if (parts.all { it.text != null }) 1 else 0)
            if (subId >= 0) put(BaseMmsColumns.SUBSCRIPTION_ID, subId)
        }
        val uri = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values)
        Log.w(TAG, "Stored outgoing MMS: $uri (thread $threadId, ${parts.size} parts)")
        if (uri == null) return null
        insertAddress(uri, "insert-address-token", PduHeaders.FROM)
        recipients.forEach { insertAddress(uri, it, PduHeaders.TO) }
        parts.forEachIndexed { index, part ->
            val partValues = ContentValues().apply {
                put(Telephony.Mms.Part.SEQ, index)
                put(Telephony.Mms.Part.CONTENT_TYPE, part.contentType)
                put(Telephony.Mms.Part.NAME, part.name)
                put(Telephony.Mms.Part.FILENAME, part.name)
                put(Telephony.Mms.Part.CONTENT_ID, "<${part.name}>")
                put(Telephony.Mms.Part.CONTENT_LOCATION, part.name)
                if (part.text != null) {
                    put(Telephony.Mms.Part.CHARSET, CharacterSets.UTF_8)
                    put(Telephony.Mms.Part.TEXT, part.text)
                }
            }
            val partUri = resolver.insert(partsUri(uri), partValues) ?: return@forEachIndexed
            if (part.text == null) resolver.openOutputStream(partUri)?.use { it.write(part.data) }
        }
        return uri
    }

    private companion object {
        const val TAG = "MmsStore"
    }

    fun markSent(uri: Uri, messageId: String?) = update(uri) {
        put(BaseMmsColumns.MESSAGE_BOX, BaseMmsColumns.MESSAGE_BOX_SENT)
        messageId?.let { put(BaseMmsColumns.MESSAGE_ID, it) }
    }

    /**
     * A send the carrier rejected goes to the failed box. (AOSP's old Messaging kept failures in
     * the outbox, but Samsung's provider clears failed MMS out of the outbox, so the message
     * vanished instead of offering a retry.)
     */
    fun markFailed(uri: Uri) {
        val rows = resolver.update(
            uri,
            ContentValues().apply {
                put(BaseMmsColumns.MESSAGE_BOX, BaseMmsColumns.MESSAGE_BOX_FAILED)
                put(BaseMmsColumns.RESPONSE_STATUS, PduHeaders.RESPONSE_STATUS_ERROR_UNSPECIFIED)
            },
            null,
            null,
        )
        val stored = resolver.query(uri, arrayOf(BaseMmsColumns.MESSAGE_BOX, BaseMmsColumns.RESPONSE_STATUS), null, null, null)
            ?.use { c -> if (c.moveToFirst()) "box=${c.getInt(0)} resp_st=${c.getInt(1)}" else "not found" }
        Log.w(TAG, "Marked $uri failed (rows=$rows, now $stored)")
    }

    /** A download that failed stays as a notification, marked so the UI offers a retry. */
    fun markDownloadFailed(uri: Uri) = update(uri) {
        put(BaseMmsColumns.STATUS, PduHeaders.STATUS_DEFERRED)
    }

    fun delete(uri: Uri) = resolver.delete(uri, null, null)

    /** A stored outgoing MMS, read back to send it again. */
    data class Outgoing(val threadId: Long, val recipients: List<String>, val parts: List<OutgoingPart>, val subId: Int)

    fun readOutgoing(uri: Uri): Outgoing? {
        val (threadId, subId) = resolver.query(uri, arrayOf(BaseMmsColumns.THREAD_ID, BaseMmsColumns.SUBSCRIPTION_ID), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) to c.getInt(1) else null } ?: return null
        val id = ContentUris.parseId(uri)
        val recipients = resolver.query(
            Uri.parse("content://mms/$id/addr"), arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} = ?", arrayOf(PduHeaders.TO.toString()), null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }.orEmpty()
        val parts = resolver.query(
            partsUri(uri),
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.NAME, Telephony.Mms.Part.TEXT),
            null, null, "${Telephony.Mms.Part.SEQ} ASC",
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val type = c.getString(1)?.lowercase() ?: continue
                    if (type == ContentType.APP_SMIL) continue
                    val name = c.getString(2) ?: "part_${c.getLong(0)}"
                    if (type == ContentType.TEXT_PLAIN) {
                        val text = c.getString(3).orEmpty()
                        add(OutgoingPart(type, text.toByteArray(), name, text = text))
                    } else {
                        val data = resolver.openInputStream(Uri.parse("content://mms/part/${c.getLong(0)}"))?.use { it.readBytes() } ?: continue
                        add(OutgoingPart(type, data, name))
                    }
                }
            }
        }.orEmpty()
        return Outgoing(threadId, recipients, parts, subId)
    }

    /** Who an outgoing message went to (joined), and its thread, for a failure notification. */
    fun describe(uri: Uri): Pair<String, Long> {
        val threadId = resolver.query(uri, arrayOf(BaseMmsColumns.THREAD_ID), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else -1L } ?: -1L
        val to = resolver.query(
            Uri.parse("content://mms/${ContentUris.parseId(uri)}/addr"),
            arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} = ?",
            arrayOf(PduHeaders.TO.toString()),
            null,
        )?.use { c -> buildList { while (c.moveToNext()) add(c.getString(0)) } }.orEmpty()
        return to.joinToString() to threadId
    }

    /**
     * The transaction id the carrier gave a stored notification. The reply to the carrier after a
     * download must carry it (the downloaded message itself usually has none).
     */
    fun transactionId(uri: Uri): ByteArray? = resolver.query(uri, arrayOf(BaseMmsColumns.TRANSACTION_ID), null, null, null)
        ?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) else null }
        ?.takeIf { it.isNotEmpty() }
        ?.toByteArray(Charsets.ISO_8859_1)

    /** Whether [uri] is still in the store (the user may delete a message while it's being retried). */
    fun exists(uri: Uri): Boolean = resolver.query(uri, arrayOf(BaseMmsColumns._ID), null, null, null)?.use { it.moveToFirst() } == true

    /** This SIM's own number, when the phone knows it (often it doesn't in India). */
    fun selfNumber(subId: Int): String? {
        val subscriptions = context.getSystemService(SubscriptionManager::class.java)
        val telephony = context.getSystemService(android.telephony.TelephonyManager::class.java)
        return listOfNotNull(
            runCatching { subscriptions?.getPhoneNumber(subId) }.getOrNull(),
            runCatching { @Suppress("DEPRECATION") telephony?.createForSubscriptionId(subId)?.line1Number }.getOrNull(),
        ).firstOrNull { it.isNotBlank() }
    }

    /** The details needed to retry a download. */
    fun notificationDetails(uri: Uri): Pair<String, Int>? = resolver.query(
        uri, arrayOf(BaseMmsColumns.CONTENT_LOCATION, BaseMmsColumns.SUBSCRIPTION_ID), null, null, null,
    )?.use { c -> if (c.moveToFirst() && !c.isNull(0)) c.getString(0) to c.getInt(1) else null }

    private fun insertPart(messageUri: Uri, seq: Int, part: PduPart, type: String): String? {
        val charset = part.charset
        val values = ContentValues().apply {
            put(Telephony.Mms.Part.SEQ, seq)
            put(Telephony.Mms.Part.CONTENT_TYPE, type)
            if (charset != 0) put(Telephony.Mms.Part.CHARSET, charset)
            part.name?.let { put(Telephony.Mms.Part.NAME, String(it)) }
            part.filename?.let { put(Telephony.Mms.Part.FILENAME, String(it)) }
            part.contentId?.let { put(Telephony.Mms.Part.CONTENT_ID, String(it)) }
            part.contentLocation?.let { put(Telephony.Mms.Part.CONTENT_LOCATION, String(it)) }
            part.contentDisposition?.let { put(Telephony.Mms.Part.CONTENT_DISPOSITION, String(it)) }
        }
        val data = part.data ?: return null
        val isText = type == ContentType.TEXT_PLAIN || type == ContentType.APP_SMIL || type == ContentType.TEXT_HTML
        if (isText) {
            val text = decode(data, charset)
            values.put(Telephony.Mms.Part.TEXT, text)
            resolver.insert(partsUri(messageUri), values)
            return text
        }
        val partUri = resolver.insert(partsUri(messageUri), values) ?: return null
        resolver.openOutputStream(partUri)?.use { it.write(data) }
        return null
    }

    private fun insertAddress(messageUri: Uri, address: String, type: Int) {
        val values = ContentValues().apply {
            put(Telephony.Mms.Addr.ADDRESS, address)
            put(Telephony.Mms.Addr.TYPE, type)
            put(Telephony.Mms.Addr.CHARSET, CharacterSets.UTF_8)
        }
        resolver.insert(Uri.parse("content://mms/${ContentUris.parseId(messageUri)}/addr"), values)
    }

    private fun partsUri(messageUri: Uri) = Uri.parse("content://mms/${ContentUris.parseId(messageUri)}/part")

    private fun ContentValues.putSubject(subject: EncodedStringValue) {
        put(BaseMmsColumns.SUBJECT, subject.string)
        put(BaseMmsColumns.SUBJECT_CHARSET, subject.characterSet)
    }

    private inline fun update(uri: Uri, values: ContentValues.() -> Unit) {
        resolver.update(uri, ContentValues().apply(values), null, null)
    }

    private fun decode(data: ByteArray, charset: Int): String = runCatching {
        if (charset == 0 || charset == CharacterSets.ANY_CHARSET) String(data, Charsets.UTF_8)
        else String(data, charset(CharacterSets.getMimeName(charset)))
    }.getOrElse { String(data, Charsets.UTF_8) }

    /** "+919876543210/TYPE=PLMN" → "+919876543210". */
    private fun cleanAddress(raw: String): String? = raw.substringBefore("/TYPE=").trim().takeIf { it.isNotEmpty() }

    /** This phone's own numbers, to leave them out of conversation recipients. Often unknown in India. */
    private fun selfNumbers(subId: Int): List<String> {
        val subscriptions = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val telephony = context.getSystemService(android.telephony.TelephonyManager::class.java)
        val ids = runCatching { subscriptions.activeSubscriptionInfoList.orEmpty().map { it.subscriptionId } }.getOrDefault(listOf(subId))
        return ids.flatMap { id ->
            listOfNotNull(
                runCatching { subscriptions.getPhoneNumber(id) }.getOrNull(),
                runCatching { @Suppress("DEPRECATION") telephony?.createForSubscriptionId(id)?.line1Number }.getOrNull(),
            )
        }.filter { it.isNotBlank() }.distinct()
    }

    private fun countryIso(): String =
        context.getSystemService(android.telephony.TelephonyManager::class.java)?.networkCountryIso?.uppercase().orEmpty().ifEmpty { "IN" }
}
