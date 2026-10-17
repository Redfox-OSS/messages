package dev.redfox.messages.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.FtsOptions
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

/**
 * One searchable message: its text plus just enough to show it as a result without touching
 * the system store. A cache of the SMS/MMS store, kept in step by the search indexer.
 *
 * [docId] is derived from the message (SMS `2·id`, MMS `2·id + 1`) since the two tables' ids
 * can collide; it is also the full-text index's row id.
 */
@Entity(tableName = "search_docs", indices = [Index(value = ["kind", "message_id"])])
data class SearchDocEntity(
    @PrimaryKey @ColumnInfo(name = "doc_id") val docId: Long,
    /** [KIND_SMS] or [KIND_MMS]. */
    @ColumnInfo(name = "kind") val kind: Int,
    @ColumnInfo(name = "message_id") val messageId: Long,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    /** Epoch millis. */
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "outgoing") val outgoing: Boolean,
    /** The sender of an incoming message; null for outgoing ones. */
    @ColumnInfo(name = "address") val address: String?,
    @ColumnInfo(name = "body") val body: String,
    /** Every suffix of every digit run in [body], so digits match anywhere (`SearchQuery.indexedNumbers`). */
    @ColumnInfo(name = "numbers") val numbers: String,
) {
    companion object {
        const val KIND_SMS = 0
        const val KIND_MMS = 1

        fun docId(kind: Int, messageId: Long): Long = messageId * 2 + kind
    }
}

/**
 * The full-text index over [SearchDocEntity] (external content: the text is stored once, in
 * `search_docs`; Room's triggers keep this index in step with every insert, update and delete).
 * Word starts of 2 and 3 letters get their own prefix indexes, so the first keystrokes are as
 * fast as whole words.
 */
@Fts4(
    contentEntity = SearchDocEntity::class,
    tokenizer = FtsOptions.TOKENIZER_UNICODE61,
    tokenizerArgs = ["remove_diacritics=2"],
    prefix = [2, 3],
)
@Entity(tableName = "search_fts")
data class SearchFtsEntity(
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "numbers") val numbers: String,
)

/** How far the index has read each table of the store, and whether a full pass has finished. */
@Entity(tableName = "search_sync")
data class SearchSyncEntity(
    @PrimaryKey @ColumnInfo(name = "kind") val kind: Int,
    /** SMS: the highest `_id` indexed. MMS: the highest text part `_id` indexed. */
    @ColumnInfo(name = "watermark") val watermark: Long,
    @ColumnInfo(name = "complete") val complete: Boolean,
)

@Dao
interface SearchDao {

    /** Newest first; [match] is an FTS4 MATCH expression (`SearchQuery.toMatch`). */
    @Query(
        "SELECT d.* FROM search_fts JOIN search_docs d ON d.doc_id = search_fts.rowid " +
            "WHERE search_fts MATCH :match ORDER BY d.timestamp DESC, d.doc_id DESC LIMIT :limit OFFSET :offset"
    )
    suspend fun page(match: String, limit: Int, offset: Int): List<SearchDocEntity>

    @Query("SELECT COUNT(*) FROM search_fts WHERE search_fts MATCH :match")
    suspend fun count(match: String): Int

    @Query("SELECT message_id FROM search_docs WHERE kind = :kind")
    suspend fun messageIds(kind: Int): List<Long>

    @Query("SELECT * FROM search_sync WHERE kind = :kind")
    suspend fun syncState(kind: Int): SearchSyncEntity?

    @Query("SELECT COUNT(*) FROM search_sync WHERE complete = 1")
    suspend fun completeKinds(): Int

    // Upsert, not REPLACE: a REPLACE deletes without firing the triggers that keep the
    // full-text index in step.
    @Upsert
    suspend fun upsert(docs: List<SearchDocEntity>)

    @Upsert
    suspend fun setSyncState(state: SearchSyncEntity)

    @Query("DELETE FROM search_docs WHERE doc_id IN (:docIds)")
    suspend fun delete(docIds: List<Long>)

    @Query("DELETE FROM search_docs WHERE kind = :kind")
    suspend fun clear(kind: Int)

    /** One batch and the position it reached, together, so a crash can't skip or repeat rows. */
    @Transaction
    suspend fun write(docs: List<SearchDocEntity>, state: SearchSyncEntity) {
        if (docs.isNotEmpty()) upsert(docs)
        setSyncState(state)
    }
}
