package dev.redfox.messages.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    // Pinned conversations lead every list; archived ones only appear under "Archived".

    @Query("SELECT * FROM conversations WHERE archived = 0 ORDER BY pinned DESC, timestamp DESC, thread_id DESC")
    fun pageAll(): PagingSource<Int, ConversationEntity>

    @Query(
        "SELECT * FROM conversations WHERE archived = 0 AND unread_count > 0 " +
            "ORDER BY pinned DESC, timestamp DESC, thread_id DESC"
    )
    fun pageUnread(): PagingSource<Int, ConversationEntity>

    @Query(
        "SELECT * FROM conversations WHERE archived = 0 AND category = :category " +
            "ORDER BY pinned DESC, timestamp DESC, thread_id DESC"
    )
    fun pageByCategory(category: String): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversations WHERE archived = 1 ORDER BY timestamp DESC, thread_id DESC")
    fun pageArchived(): PagingSource<Int, ConversationEntity>

    /** Applies a pin / archive change to the index at once, without waiting for the next sync. */
    @Query("UPDATE conversations SET pinned = :pinned, archived = :archived WHERE thread_id = :threadId")
    suspend fun setFlags(threadId: Long, pinned: Boolean, archived: Boolean)

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun observe(threadId: Long): Flow<ConversationEntity?>

    /** Search: [pattern] is a LIKE pattern over the encoded participants (names and numbers). */
    @Query(
        "SELECT * FROM conversations WHERE participants LIKE :pattern ESCAPE '\\' " +
            "ORDER BY pinned DESC, timestamp DESC LIMIT :limit"
    )
    suspend fun searchParticipants(pattern: String, limit: Int): List<ConversationEntity>

    @Query("SELECT * FROM conversations WHERE thread_id IN (:threadIds)")
    suspend fun byIds(threadIds: List<Long>): List<ConversationEntity>

    @Query("SELECT COUNT(*) FROM conversations")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM conversations")
    suspend fun all(): List<ConversationEntity>

    @Upsert
    suspend fun upsert(rows: List<ConversationEntity>)

    @Query("DELETE FROM conversations WHERE thread_id IN (:threadIds)")
    suspend fun delete(threadIds: List<Long>)

    /**
     * Applies a fresh snapshot of the SMS store, writing only rows that changed so Room
     * invalidates (and the paged list re-diffs) only when something really changed.
     * Returns true when anything was written.
     */
    @Transaction
    suspend fun sync(snapshot: List<ConversationEntity>): Boolean {
        val existing = all().associateBy { it.threadId }
        val changed = snapshot.filter { existing[it.threadId] != it }
        val fresh = snapshot.mapTo(HashSet()) { it.threadId }
        val removed = existing.keys.filter { it !in fresh }
        if (changed.isNotEmpty()) changed.chunked(CHUNK).forEach { upsert(it) }
        if (removed.isNotEmpty()) removed.chunked(CHUNK).forEach { delete(it) }
        return changed.isNotEmpty() || removed.isNotEmpty()
    }

    private companion object {
        /** Well under SQLite's bound-variable limit. */
        const val CHUNK = 500
    }
}
