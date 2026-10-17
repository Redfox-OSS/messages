package dev.redfox.messages.core.database

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Data only the user creates: starred messages, scheduled messages, and per-conversation
 * pin / archive. Unlike the inbox index ([MessagesDatabase]) this can't be rebuilt from the SMS
 * store, so it is never wiped: every schema change ships a real migration.
 */
@Database(
    entities = [StarredMessageEntity::class, ScheduledMessageEntity::class, ConversationPrefsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class UserDatabase : RoomDatabase() {
    abstract fun starred(): StarredDao
    abstract fun scheduled(): ScheduledDao
    abstract fun conversationPrefs(): ConversationPrefsDao
}

@Entity(tableName = "starred_messages", indices = [Index("thread_id"), Index("starred_at")])
data class StarredMessageEntity(
    /** "Sms-123" / "Mms-45". */
    @PrimaryKey @ColumnInfo(name = "message_key") val key: String,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "thread_title") val threadTitle: String,
    @ColumnInfo(name = "body") val body: String,
    /** Null for the user's own messages. */
    @ColumnInfo(name = "sender_address") val senderAddress: String?,
    @ColumnInfo(name = "sender_name") val senderName: String?,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "starred_at") val starredAt: Long,
)

@Dao
interface StarredDao {
    @Query("SELECT * FROM starred_messages ORDER BY starred_at DESC")
    fun observeAll(): Flow<List<StarredMessageEntity>>

    @Query("SELECT message_key FROM starred_messages WHERE thread_id = :threadId")
    fun observeKeys(threadId: Long): Flow<List<String>>

    @Upsert
    suspend fun upsert(entity: StarredMessageEntity)

    @Query("DELETE FROM starred_messages WHERE message_key = :key")
    suspend fun delete(key: String)
}

@Entity(tableName = "scheduled_messages", indices = [Index("send_at"), Index("thread_id")])
data class ScheduledMessageEntity(
    @PrimaryKey(autoGenerate = true) @ColumnInfo(name = "id") val id: Long = 0,
    @ColumnInfo(name = "thread_id") val threadId: Long,
    /** Recipients separated by ASCII unit separators (never in phone numbers). */
    @ColumnInfo(name = "recipients") val recipients: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "body") val body: String,
    @ColumnInfo(name = "subscription_id") val subscriptionId: Int,
    @ColumnInfo(name = "send_at") val sendAt: Long,
    @ColumnInfo(name = "failed", defaultValue = "0") val failed: Boolean = false,
)

@Dao
interface ScheduledDao {
    @Query("SELECT * FROM scheduled_messages ORDER BY send_at ASC")
    fun observeAll(): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE thread_id = :threadId ORDER BY send_at ASC")
    fun observeForThread(threadId: Long): Flow<List<ScheduledMessageEntity>>

    @Query("SELECT * FROM scheduled_messages WHERE failed = 0")
    suspend fun pending(): List<ScheduledMessageEntity>

    @Query("SELECT * FROM scheduled_messages WHERE id = :id")
    suspend fun get(id: Long): ScheduledMessageEntity?

    @Insert
    suspend fun insert(entity: ScheduledMessageEntity): Long

    @Query("UPDATE scheduled_messages SET send_at = :sendAt, failed = 0 WHERE id = :id")
    suspend fun reschedule(id: Long, sendAt: Long)

    @Query("UPDATE scheduled_messages SET failed = 1 WHERE id = :id")
    suspend fun markFailed(id: Long)

    @Query("DELETE FROM scheduled_messages WHERE id = :id")
    suspend fun delete(id: Long)
}

@Entity(tableName = "conversation_prefs")
data class ConversationPrefsEntity(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: Long,
    @ColumnInfo(name = "pinned", defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean = false,
)

@Dao
interface ConversationPrefsDao {
    @Query("SELECT * FROM conversation_prefs WHERE pinned = 1 OR archived = 1")
    suspend fun all(): List<ConversationPrefsEntity>

    @Query("SELECT * FROM conversation_prefs WHERE thread_id = :threadId")
    suspend fun get(threadId: Long): ConversationPrefsEntity?

    @Upsert
    suspend fun upsert(entity: ConversationPrefsEntity)

    @Query("DELETE FROM conversation_prefs WHERE thread_id = :threadId")
    suspend fun delete(threadId: Long)
}
