package dev.redfox.messages.core.database

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY timestamp DESC, thread_id DESC")
    fun pageAll(): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversations WHERE unread_count > 0 ORDER BY timestamp DESC, thread_id DESC")
    fun pageUnread(): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversations WHERE category = :category ORDER BY timestamp DESC, thread_id DESC")
    fun pageByCategory(category: String): PagingSource<Int, ConversationEntity>

    @Query("SELECT * FROM conversations WHERE thread_id = :threadId")
    fun observe(threadId: Long): Flow<ConversationEntity?>

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
