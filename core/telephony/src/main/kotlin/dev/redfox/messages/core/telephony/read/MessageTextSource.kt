package dev.redfox.messages.core.telephony.read

import android.content.ContentResolver
import android.content.Context
import android.provider.Telephony
import android.provider.Telephony.BaseMmsColumns
import android.provider.Telephony.TextBasedSmsColumns
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.domain.RichMessageParser
import javax.inject.Inject
import javax.inject.Singleton

/** The searchable text of one message, read for the search index. */
data class MessageText(
    val id: Long,
    val threadId: Long,
    /** Epoch millis. */
    val timestamp: Long,
    val outgoing: Boolean,
    /** The sender of an incoming message. */
    val address: String?,
    val body: String,
)

/** MMS ids whose text parts changed after a watermark, and the highest text part `_id` seen. */
data class MmsTextChanges(val messageIds: Set<Long>, val maxPartId: Long)

/**
 * Reads message text from the system store for the search index, in id order so the index can
 * resume where it stopped. Blocking; call from an IO dispatcher. Drafts are left out.
 */
@Singleton
class MessageTextSource @Inject constructor(
    @param:ApplicationContext context: Context,
) {
    private val resolver: ContentResolver = context.contentResolver

    /** Every SMS `_id` (drafts excluded), ascending: one small column, for spotting deletions. */
    fun smsIds(): LongArray = ids(Telephony.Sms.CONTENT_URI, Telephony.Sms._ID, SMS_NOT_DRAFT)

    /** Every MMS `_id` (drafts excluded), ascending. */
    fun mmsIds(): LongArray = ids(Telephony.Mms.CONTENT_URI, Telephony.Mms._ID, MMS_NOT_DRAFT)

    /** Up to [limit] SMS with `_id` above [afterId], ascending. */
    fun smsAfter(afterId: Long, limit: Int): List<MessageText> {
        val out = ArrayList<MessageText>(limit)
        resolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID, TextBasedSmsColumns.THREAD_ID, TextBasedSmsColumns.DATE,
                TextBasedSmsColumns.TYPE, TextBasedSmsColumns.ADDRESS, TextBasedSmsColumns.BODY),
            "${Telephony.Sms._ID} > ? AND $SMS_NOT_DRAFT",
            arrayOf(afterId.toString()),
            "${Telephony.Sms._ID} ASC LIMIT $limit",
        )?.use { c ->
            while (c.moveToNext()) {
                val outgoing = c.getInt(3) != TextBasedSmsColumns.MESSAGE_TYPE_INBOX
                out += MessageText(
                    id = c.getLong(0),
                    threadId = c.getLong(1),
                    timestamp = c.getLong(2),
                    outgoing = outgoing,
                    address = if (outgoing) null else c.getString(4),
                    body = c.getString(5).orEmpty(),
                )
            }
        }
        return out
    }

    /**
     * MMS whose text parts were added after part [afterPartId]. Parts, not messages: a received
     * MMS gets its id when the notification arrives and its text only once downloaded.
     */
    fun mmsTextChangesAfter(afterPartId: Long): MmsTextChanges {
        val ids = HashSet<Long>()
        var max = afterPartId
        resolver.query(
            MMS_PARTS,
            arrayOf(Telephony.Mms.Part._ID, Telephony.Mms.Part.MSG_ID),
            "${Telephony.Mms.Part._ID} > ? AND ${Telephony.Mms.Part.CONTENT_TYPE} = 'text/plain'",
            arrayOf(afterPartId.toString()),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                max = maxOf(max, c.getLong(0))
                ids += c.getLong(1)
            }
        }
        return MmsTextChanges(ids, max)
    }

    /** The highest text part `_id` in the store (0 when there are none). */
    fun maxMmsTextPartId(): Long =
        resolver.query(
            MMS_PARTS,
            arrayOf(Telephony.Mms.Part._ID),
            "${Telephony.Mms.Part.CONTENT_TYPE} = 'text/plain'",
            null,
            "${Telephony.Mms.Part._ID} DESC LIMIT 1",
        )?.use { c -> if (c.moveToFirst()) c.getLong(0) else 0L } ?: 0L

    /** The text of each MMS in [ids] that has any (chatbot card markup left out). */
    fun mms(ids: Collection<Long>): List<MessageText> {
        if (ids.isEmpty()) return emptyList()
        val texts = HashMap<Long, StringBuilder>()
        ids.chunked(IN_CHUNK).forEach { chunk ->
            resolver.query(
                MMS_PARTS,
                arrayOf(Telephony.Mms.Part.MSG_ID, Telephony.Mms.Part.TEXT),
                "${Telephony.Mms.Part.MSG_ID} IN (${chunk.joinToString(",")}) AND ${Telephony.Mms.Part.CONTENT_TYPE} = 'text/plain'",
                null,
                "${Telephony.Mms.Part.SEQ} ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val text = c.getString(1).orEmpty()
                    if (text.isBlank() || RichMessageParser.looksLikeRichMessage(text)) continue
                    texts.getOrPut(c.getLong(0)) { StringBuilder() }.apply {
                        if (isNotEmpty()) append('\n')
                        append(text)
                    }
                }
            }
        }
        if (texts.isEmpty()) return emptyList()
        val out = ArrayList<MessageText>(texts.size)
        texts.keys.chunked(IN_CHUNK).forEach { chunk ->
            resolver.query(
                Telephony.Mms.CONTENT_URI,
                arrayOf(Telephony.Mms._ID, BaseMmsColumns.THREAD_ID, BaseMmsColumns.DATE, BaseMmsColumns.MESSAGE_BOX),
                "${Telephony.Mms._ID} IN (${chunk.joinToString(",")}) AND $MMS_NOT_DRAFT",
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val outgoing = c.getInt(3) != BaseMmsColumns.MESSAGE_BOX_INBOX
                    out += MessageText(
                        id = id,
                        threadId = c.getLong(1),
                        timestamp = c.getLong(2) * 1000,
                        outgoing = outgoing,
                        address = if (outgoing) null else sender(id),
                        body = texts.getValue(id).toString(),
                    )
                }
            }
        }
        return out
    }

    private fun sender(mmsId: Long): String? =
        resolver.query(
            "content://mms/$mmsId/addr".toUri(),
            arrayOf(Telephony.Mms.Addr.ADDRESS),
            "${Telephony.Mms.Addr.TYPE} = $PDU_FROM",
            null,
            null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?.takeIf { it.isNotBlank() && it != INSERT_ADDRESS_TOKEN }

    private fun ids(uri: android.net.Uri, column: String, selection: String): LongArray =
        resolver.query(uri, arrayOf(column), selection, null, "$column ASC")?.use { c ->
            LongArray(c.count) { i -> c.moveToPosition(i); c.getLong(0) }
        } ?: LongArray(0)

    private companion object {
        val MMS_PARTS = "content://mms/part".toUri()
        const val SMS_NOT_DRAFT = "${TextBasedSmsColumns.TYPE} != ${TextBasedSmsColumns.MESSAGE_TYPE_DRAFT}"
        const val MMS_NOT_DRAFT = "${BaseMmsColumns.MESSAGE_BOX} != ${BaseMmsColumns.MESSAGE_BOX_DRAFTS}"
        const val PDU_FROM = 137
        const val INSERT_ADDRESS_TOKEN = "insert-address-token"
        const val IN_CHUNK = 400
    }
}
