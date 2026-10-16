package dev.redfox.messages.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * App-owned data (PLAN.md §5). Today: the inbox index. Next: pin / archive / mute metadata,
 * scheduled messages, block rules and the FTS search index.
 */
@Database(entities = [ConversationEntity::class], version = 1, exportSchema = true)
abstract class MessagesDatabase : RoomDatabase() {
    abstract fun conversations(): ConversationDao
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): MessagesDatabase =
        Room.databaseBuilder(context, MessagesDatabase::class.java, "messages.db")
            // The index is a rebuildable cache of the system store, so a schema bump may simply drop it.
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun conversationDao(database: MessagesDatabase): ConversationDao = database.conversations()
}
