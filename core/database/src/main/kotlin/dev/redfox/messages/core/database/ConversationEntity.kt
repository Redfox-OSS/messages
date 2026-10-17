package dev.redfox.messages.core.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One inbox row, denormalised so the paged list needs no joins or provider reads. A cache of
 * the system SMS/MMS store (the source of truth), rebuilt by the sync whenever the store changes.
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["archived", "pinned", "timestamp"]),
        Index(value = ["archived", "category", "timestamp"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey @ColumnInfo(name = "thread_id") val threadId: Long,
    /** Participants, encoded by [ParticipantCodec]: address, name, photo, formatted address. */
    @ColumnInfo(name = "participants") val participants: String,
    @ColumnInfo(name = "snippet") val snippet: String,
    @ColumnInfo(name = "timestamp") val timestamp: Long,
    @ColumnInfo(name = "unread_count") val unreadCount: Int,
    @ColumnInfo(name = "message_count") val messageCount: Int,
    @ColumnInfo(name = "has_attachment") val hasAttachment: Boolean,
    /** `MessageCategory.name`. */
    @ColumnInfo(name = "category") val category: String,
    /** Copied from the user's conversation settings (`user.db`) at every sync, for sorting and filtering. */
    @ColumnInfo(name = "pinned", defaultValue = "0") val pinned: Boolean = false,
    @ColumnInfo(name = "archived", defaultValue = "0") val archived: Boolean = false,
)
