package dev.redfox.messages.core.telephony.read

import android.content.ContentResolver
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.Telephony
import android.provider.Telephony.BaseMmsColumns
import android.provider.Telephony.TextBasedSmsColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.model.Attachment
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.model.DeliveryStatus
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageBox
import dev.redfox.messages.core.model.MessageKind
import dev.redfox.messages.core.model.RichContent
import dev.redfox.messages.core.domain.RichMessageParser
import javax.inject.Inject
import javax.inject.Singleton

/** A conversation summary built from the SMS and MMS tables, addresses not yet resolved to contacts. */
data class RawThread(
    val threadId: Long,
    val addresses: List<String>,
    val snippet: String,
    val timestamp: Long,
    val messageCount: Int,
    val unreadCount: Int,
    val hasAttachment: Boolean,
)

/** Where a table's paging stopped: its own `date` units (ms for SMS, seconds for MMS) and `_id`. */
data class Position(val date: Long, val id: Long)

/** Paging key for a thread: one position per table, plus whether each table is exhausted. */
data class MessagePageKey(
    val sms: Position? = null,
    val mms: Position? = null,
    val smsDone: Boolean = false,
    val mmsDone: Boolean = false,
)

/** A page of messages, newest first; [nextKey] is null at the start of the conversation. */
data class MessagePage(val messages: List<Message>, val nextKey: MessagePageKey?)

/**
 * Reads the system Telephony provider: the one store every SMS app (Samsung Messages, Google
 * Messages, us) shares. Blocking; call from an IO dispatcher. Throws [SecurityException]
 * without READ_SMS.
 *
 * Messages come back with senders as bare addresses; contact resolution happens a layer up.
 */
@Singleton
class TelephonyReader @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val resolver: ContentResolver = context.contentResolver

    // ---------------------------------------------------------------------------------------
    // Threads
    // ---------------------------------------------------------------------------------------

    /**
     * Every conversation, newest first, aggregated from the `sms` and `mms` tables.
     *
     * Deliberately avoids `content://mms-sms/conversations`: OEMs rewrite that view (Samsung
     * ignores `?simple=true` and drops `recipient_ids`), while the per-message tables have the
     * same shape everywhere. Two light passes plus a snippet lookup for the newest message only.
     */
    fun readThreads(): List<RawThread> {
        val threads = LinkedHashMap<Long, ThreadAcc>()

        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, TextBasedSmsColumns.THREAD_ID, TextBasedSmsColumns.ADDRESS,
                TextBasedSmsColumns.DATE, TextBasedSmsColumns.READ, TextBasedSmsColumns.TYPE),
            "${TextBasedSmsColumns.TYPE} != ${TextBasedSmsColumns.MESSAGE_TYPE_DRAFT}",
            null,
            "${TextBasedSmsColumns.DATE} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(Telephony.Sms._ID)
            val tid = c.getColumnIndexOrThrow(TextBasedSmsColumns.THREAD_ID)
            val address = c.getColumnIndexOrThrow(TextBasedSmsColumns.ADDRESS)
            val date = c.getColumnIndexOrThrow(TextBasedSmsColumns.DATE)
            val read = c.getColumnIndexOrThrow(TextBasedSmsColumns.READ)
            val type = c.getColumnIndexOrThrow(TextBasedSmsColumns.TYPE)
            while (c.moveToNext()) {
                val acc = threads.getOrPut(c.getLong(tid)) { ThreadAcc() }
                acc.count++
                if (acc.latestDate == 0L) { // Sorted newest first: the first row is the latest.
                    acc.latestDate = c.getLong(date)
                    acc.latestSmsId = c.getLong(id)
                }
                if (!c.isNull(address) && acc.addresses.size < MAX_PARTICIPANTS) acc.addresses += c.getString(address)
                if (c.getInt(type) == TextBasedSmsColumns.MESSAGE_TYPE_INBOX && c.getInt(read) == 0) acc.unread++
            }
        }

        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID, BaseMmsColumns.THREAD_ID, BaseMmsColumns.DATE, BaseMmsColumns.READ, BaseMmsColumns.MESSAGE_BOX),
            "${BaseMmsColumns.MESSAGE_BOX} != ${BaseMmsColumns.MESSAGE_BOX_DRAFTS}",
            null,
            "${BaseMmsColumns.DATE} DESC",
        )?.use { c ->
            val id = c.getColumnIndexOrThrow(Telephony.Mms._ID)
            val tid = c.getColumnIndexOrThrow(BaseMmsColumns.THREAD_ID)
            val date = c.getColumnIndexOrThrow(BaseMmsColumns.DATE)
            val read = c.getColumnIndexOrThrow(BaseMmsColumns.READ)
            val box = c.getColumnIndexOrThrow(BaseMmsColumns.MESSAGE_BOX)
            while (c.moveToNext()) {
                val acc = threads.getOrPut(c.getLong(tid)) { ThreadAcc() }
                val millis = c.getLong(date) * 1000 // MMS dates are in seconds.
                acc.count++
                acc.hasMms = true
                if (acc.latestMmsId == null) acc.latestMmsId = c.getLong(id)
                if (millis > acc.latestDate) {
                    acc.latestDate = millis
                    acc.latestIsMms = true
                }
                if (c.getInt(box) == BaseMmsColumns.MESSAGE_BOX_INBOX && c.getInt(read) == 0) acc.unread++
            }
        }

        // Group / MMS-only threads have no SMS address: take participants from their newest MMS.
        threads.values.filter { it.addresses.isEmpty() }.forEach { acc ->
            acc.latestMmsId?.let { acc.addresses += readMmsParticipants(it) }
        }

        val smsSnippets = readSmsBodies(threads.values.filter { !it.latestIsMms }.mapNotNull { it.latestSmsId })
        val mmsSnippets = readParts(threads.values.filter { it.latestIsMms }.mapNotNull { it.latestMmsId })

        return threads.entries
            .map { (threadId, acc) ->
                val snippet = if (acc.latestIsMms) {
                    (mmsSnippets[acc.latestMmsId] ?: MmsContent()).snippet
                } else {
                    smsSnippets[acc.latestSmsId].orEmpty()
                }
                RawThread(
                    threadId = threadId,
                    addresses = acc.addresses.toList(),
                    snippet = snippet,
                    timestamp = acc.latestDate,
                    messageCount = acc.count,
                    unreadCount = acc.unread,
                    hasAttachment = acc.hasMms,
                )
            }
            .sortedByDescending { it.timestamp }
    }

    private class ThreadAcc {
        var latestDate = 0L
        var latestSmsId: Long? = null
        var latestMmsId: Long? = null
        var latestIsMms = false
        var hasMms = false
        var count = 0
        var unread = 0
        val addresses = LinkedHashSet<String>()
    }

    private fun readSmsBodies(ids: List<Long>): Map<Long, String> {
        val bodies = HashMap<Long, String>(ids.size)
        ids.chunked(IN_CHUNK).forEach { chunk ->
            resolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf(Telephony.Sms._ID, TextBasedSmsColumns.BODY),
                "${Telephony.Sms._ID} IN (${chunk.joinToString(",")})",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) bodies[c.getLong(0)] = c.getString(1).orEmpty()
            }
        }
        return bodies
    }

    /** Everyone on an MMS except ourselves (the "insert-address-token" placeholder). */
    private fun readMmsParticipants(mmsId: Long): List<String> =
        resolver.query(
            "content://mms/$mmsId/addr".toUri(),
            arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} IN ($PDU_FROM, $PDU_TO, $PDU_CC)",
            null,
            null,
        )?.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val address = c.getString(0)
                    if (!address.isNullOrBlank() && address != INSERT_ADDRESS_TOKEN && address !in this) add(address)
                }
            }
        }.orEmpty().take(MAX_PARTICIPANTS)

    // ---------------------------------------------------------------------------------------
    // Messages
    // ---------------------------------------------------------------------------------------

    /**
     * One page of [threadId], newest first: up to [limit] messages strictly older than [key]
     * (null = start from the newest). Keyset paging — each page costs the same however far
     * back it is, unlike offsets. SMS and MMS are separate tables, so each keeps its own
     * position; MMS dates are whole seconds, so `_id` breaks ties exactly.
     */
    fun readPage(threadId: Long, key: MessagePageKey?, limit: Int): MessagePage {
        val start = key ?: MessagePageKey()
        val sms = if (start.smsDone) emptyList() else readSms(threadId, start.sms, limit)
        val mms = if (start.mmsDone) emptyList() else readMms(threadId, start.mms, limit)

        val page = (sms + mms)
            .sortedWith(compareByDescending<Message> { it.timestamp }.thenByDescending { it.id })
            .take(limit)

        // Advance each table to the oldest of its rows actually shown on this page.
        val lastSms = page.lastOrNull { it.kind == MessageKind.Sms }
        val lastMms = page.lastOrNull { it.kind == MessageKind.Mms }
        val next = MessagePageKey(
            sms = lastSms?.let { Position(it.timestamp, it.id) } ?: start.sms,
            mms = lastMms?.let { Position(it.timestamp / 1000, it.id) } ?: start.mms,
            // A table is exhausted when it returned a short read and every row made the page.
            smsDone = start.smsDone || (sms.size < limit && sms.all { it in page }),
            mmsDone = start.mmsDone || (mms.size < limit && mms.all { it in page }),
        )
        return MessagePage(page, nextKey = next.takeUnless { it.smsDone && it.mmsDone })
    }

    private fun readSms(threadId: Long, before: Position?, max: Int): List<Message> {
        val out = ArrayList<Message>(minOf(max, 256))
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID,
                TextBasedSmsColumns.ADDRESS,
                TextBasedSmsColumns.BODY,
                TextBasedSmsColumns.DATE,
                TextBasedSmsColumns.TYPE,
                TextBasedSmsColumns.READ,
                TextBasedSmsColumns.SUBSCRIPTION_ID,
                TextBasedSmsColumns.STATUS,
            ),
            keyset("${TextBasedSmsColumns.THREAD_ID} = ?", TextBasedSmsColumns.DATE, Telephony.Sms._ID, before),
            keysetArgs(threadId, before),
            "${TextBasedSmsColumns.DATE} DESC, ${Telephony.Sms._ID} DESC",
        )?.use { c ->
            while (out.size < max && c.moveToNext()) {
                val box = smsBox(c.int(TextBasedSmsColumns.TYPE))
                val address = c.string(TextBasedSmsColumns.ADDRESS)
                out += Message(
                    id = c.long(Telephony.Sms._ID),
                    threadId = threadId,
                    kind = MessageKind.Sms,
                    box = box,
                    sender = if (box == MessageBox.Inbox && address != null) Contact(address) else null,
                    body = c.string(TextBasedSmsColumns.BODY).orEmpty(),
                    timestamp = c.long(TextBasedSmsColumns.DATE),
                    isRead = c.int(TextBasedSmsColumns.READ) == 1,
                    subscriptionId = c.int(TextBasedSmsColumns.SUBSCRIPTION_ID),
                    deliveryStatus = if (box == MessageBox.Inbox) DeliveryStatus.None
                    else deliveryStatus(c.intOrNull(TextBasedSmsColumns.STATUS) ?: TextBasedSmsColumns.STATUS_NONE),
                )
            }
        }
        return out
    }

    /** SMS `status`: -1 none requested, 0 delivered, 32 pending, 64 failed. */
    private fun deliveryStatus(status: Int) = when {
        status == TextBasedSmsColumns.STATUS_COMPLETE -> DeliveryStatus.Delivered
        status >= TextBasedSmsColumns.STATUS_FAILED -> DeliveryStatus.Failed
        status >= TextBasedSmsColumns.STATUS_PENDING -> DeliveryStatus.Pending
        else -> DeliveryStatus.None
    }

    private fun readMms(threadId: Long, before: Position?, max: Int): List<Message> {
        data class Header(
            val id: Long, val box: MessageBox, val timestamp: Long, val read: Boolean,
            val subId: Int, val subject: String?, val pending: Boolean, val downloadFailed: Boolean,
        )
        val headers = ArrayList<Header>()
        resolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID,
                BaseMmsColumns.DATE,
                BaseMmsColumns.MESSAGE_BOX,
                BaseMmsColumns.READ,
                BaseMmsColumns.SUBSCRIPTION_ID,
                BaseMmsColumns.SUBJECT,
                BaseMmsColumns.MESSAGE_TYPE,
                BaseMmsColumns.RESPONSE_STATUS,
                BaseMmsColumns.STATUS,
            ),
            keyset("${BaseMmsColumns.THREAD_ID} = ?", BaseMmsColumns.DATE, Telephony.Mms._ID, before),
            keysetArgs(threadId, before),
            "${BaseMmsColumns.DATE} DESC, ${Telephony.Mms._ID} DESC",
        )?.use { c ->
            while (headers.size < max && c.moveToNext()) {
                val rawBox = mmsBox(c.int(BaseMmsColumns.MESSAGE_BOX))
                // MMS has no failed box: a failed send stays in the outbox with an error response status.
                val responseStatus = c.intOrNull(BaseMmsColumns.RESPONSE_STATUS) ?: 0
                val dateMillis = c.long(BaseMmsColumns.DATE) * 1000
                // Samsung's provider can silently ignore our "failed" update, so an MMS that's still
                // "sending" long after the MMS service's last retry (about 5 minutes) has failed.
                val stale = rawBox == MessageBox.Outbox && System.currentTimeMillis() - dateMillis > STALE_MMS_SEND_MILLIS
                val box = if (rawBox == MessageBox.Outbox && (responseStatus > RESPONSE_STATUS_OK || stale)) MessageBox.Failed else rawBox
                headers += Header(
                    id = c.long(Telephony.Mms._ID),
                    box = box,
                    timestamp = c.long(BaseMmsColumns.DATE) * 1000, // MMS dates are in seconds.
                    read = c.int(BaseMmsColumns.READ) == 1,
                    subId = c.int(BaseMmsColumns.SUBSCRIPTION_ID),
                    subject = c.string(BaseMmsColumns.SUBJECT)?.takeIf { it.isNotBlank() },
                    pending = c.int(BaseMmsColumns.MESSAGE_TYPE) == MESSAGE_TYPE_NOTIFICATION_IND,
                    downloadFailed = c.intOrNull(BaseMmsColumns.STATUS) == STATUS_DEFERRED,
                )
            }
        }
        if (headers.isEmpty()) return emptyList()

        val parts = readParts(headers.map { it.id })
        return headers.map { h ->
            val content = parts[h.id] ?: MmsContent()
            Message(
                id = h.id,
                threadId = threadId,
                kind = MessageKind.Mms,
                box = h.box,
                sender = if (h.box == MessageBox.Inbox) readMmsSender(h.id)?.let(::Contact) else null,
                body = content.text,
                timestamp = h.timestamp,
                isRead = h.read,
                subscriptionId = h.subId,
                attachments = content.attachments,
                subject = h.subject,
                isPendingDownload = h.pending,
                isDownloadFailed = h.pending && h.downloadFailed,
                rich = content.rich,
            )
        }
    }

    /** The readable content of one MMS: its text, its media, and a parsed chatbot card if it is one. */
    private data class MmsContent(
        val text: String = "",
        val attachments: List<Attachment> = emptyList(),
        val rich: RichContent? = null,
    ) {
        val snippet: String
            get() = rich?.summary
                ?: text.ifBlank { if (attachments.isNotEmpty()) "Attachment" else "Multimedia message" }
    }

    /**
     * Parts for several MMS at once (one query instead of one per message). RCS chatbot cards
     * arrive either as a `botmessage` part (JSON in the part's file) or as a text part holding
     * the same JSON; both become [RichContent] instead of raw JSON text.
     */
    private fun readParts(mmsIds: List<Long>): Map<Long, MmsContent> {
        val texts = HashMap<Long, StringBuilder>()
        val media = HashMap<Long, MutableList<Attachment>>()
        val rich = HashMap<Long, RichContent>()
        mmsIds.chunked(IN_CHUNK).forEach { chunk ->
            resolver.query(
                MMS_PARTS,
                arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.MSG_ID, Telephony.Mms.Part.CONTENT_TYPE,
                    Telephony.Mms.Part.TEXT, Telephony.Mms.Part.NAME, Telephony.Mms.Part.CONTENT_LOCATION),
                "${Telephony.Mms.Part.MSG_ID} IN (${chunk.joinToString(",")})",
                null,
                "${Telephony.Mms.Part.SEQ} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val mid = c.long(Telephony.Mms.Part.MSG_ID)
                    val partId = c.long(Telephony.Mms.Part._ID)
                    val type = c.string(Telephony.Mms.Part.CONTENT_TYPE).orEmpty().lowercase()
                    when {
                        type.startsWith(RichMessageParser.CONTENT_TYPE_PREFIX) ->
                            readPartFile(partId)?.let(RichMessageParser::parse)?.let { rich[mid] = it }

                        type == "text/plain" -> {
                            val text = c.string(Telephony.Mms.Part.TEXT).orEmpty()
                            val card = if (RichMessageParser.looksLikeRichMessage(text)) RichMessageParser.parse(text) else null
                            when {
                                card != null -> rich[mid] = card
                                text.isNotBlank() -> texts.getOrPut(mid) { StringBuilder() }.apply {
                                    if (isNotEmpty()) append('\n')
                                    append(text)
                                }
                            }
                        }

                        type == "application/smil" -> Unit // Layout only.

                        else -> media.getOrPut(mid) { mutableListOf() } += Attachment(
                            uri = "$MMS_PARTS/$partId",
                            mimeType = type,
                            name = c.string(Telephony.Mms.Part.NAME) ?: c.string(Telephony.Mms.Part.CONTENT_LOCATION),
                        )
                    }
                }
            }
        }
        return (texts.keys + media.keys + rich.keys).associateWith { id ->
            MmsContent(texts[id]?.toString().orEmpty(), media[id].orEmpty(), rich[id])
        }
    }

    /** A part stored as a file (e.g. a chatbot card's JSON), capped so a bad part can't exhaust memory. */
    private fun readPartFile(partId: Long): String? = runCatching {
        resolver.openInputStream("$MMS_PARTS/$partId".toUri())?.use { stream ->
            val bytes = stream.readNBytes(MAX_PART_BYTES)
            String(bytes, Charsets.UTF_8)
        }
    }.getOrNull()

    private fun readMmsSender(mmsId: Long): String? =
        resolver.query(
            "content://mms/$mmsId/addr".toUri(),
            arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} = $PDU_FROM",
            null,
            null,
        )?.use { c -> if (c.moveToFirst()) c.string(Telephony.Mms.Addr.ADDRESS) else null }
            ?.takeIf { it.isNotBlank() && it != INSERT_ADDRESS_TOKEN }

    /** `thread = ? AND (date < ? OR (date = ? AND _id < ?))` — strictly older than [before]. */
    private fun keyset(base: String, date: String, id: String, before: Position?) =
        if (before == null) base else "$base AND ($date < ? OR ($date = ? AND $id < ?))"

    private fun keysetArgs(threadId: Long, before: Position?): Array<String> =
        if (before == null) arrayOf(threadId.toString())
        else arrayOf(threadId.toString(), before.date.toString(), before.date.toString(), before.id.toString())

    private fun smsBox(type: Int) = when (type) {
        TextBasedSmsColumns.MESSAGE_TYPE_INBOX -> MessageBox.Inbox
        TextBasedSmsColumns.MESSAGE_TYPE_SENT -> MessageBox.Sent
        TextBasedSmsColumns.MESSAGE_TYPE_DRAFT -> MessageBox.Draft
        TextBasedSmsColumns.MESSAGE_TYPE_OUTBOX -> MessageBox.Outbox
        TextBasedSmsColumns.MESSAGE_TYPE_FAILED -> MessageBox.Failed
        TextBasedSmsColumns.MESSAGE_TYPE_QUEUED -> MessageBox.Queued
        else -> MessageBox.Inbox
    }

    private fun mmsBox(box: Int) = when (box) {
        BaseMmsColumns.MESSAGE_BOX_INBOX -> MessageBox.Inbox
        BaseMmsColumns.MESSAGE_BOX_SENT -> MessageBox.Sent
        BaseMmsColumns.MESSAGE_BOX_DRAFTS -> MessageBox.Draft
        BaseMmsColumns.MESSAGE_BOX_OUTBOX -> MessageBox.Outbox
        BaseMmsColumns.MESSAGE_BOX_FAILED -> MessageBox.Failed
        else -> MessageBox.Inbox
    }

    private companion object {
        val MMS_PARTS: Uri = "content://mms/part".toUri()
        const val MESSAGE_TYPE_NOTIFICATION_IND = 130
        /** X-Mms-Response-Status "Ok"; anything above is an error (OMA MMS ENC §7.3.48). */
        const val RESPONSE_STATUS_OK = 0x80
        const val STALE_MMS_SEND_MILLIS = 10 * 60_000L
        /** X-Mms-Status "Deferred": we mark failed downloads this way. */
        const val STATUS_DEFERRED = 0x83
        const val PDU_FROM = 137
        const val PDU_TO = 151
        const val PDU_CC = 130
        const val MAX_PARTICIPANTS = 12
        /** Keeps `IN (…)` lists well under SQLite's variable/length limits. */
        const val IN_CHUNK = 400
        /** Chatbot card JSON is a few KB; anything past this isn't a card. */
        const val MAX_PART_BYTES = 256 * 1024
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"
    }
}

private fun Cursor.string(column: String): String? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getString)

private fun Cursor.long(column: String): Long =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getLong) ?: 0L

private fun Cursor.int(column: String): Int = intOrNull(column) ?: 0

private fun Cursor.intOrNull(column: String): Int? =
    getColumnIndex(column).takeIf { it >= 0 && !isNull(it) }?.let(::getInt)
