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
 * The inbox index (PLAN.md §5): a rebuildable cache of the system SMS/MMS store. User-created
 * data (stars, schedules, pins) lives in [UserDatabase], which is never wiped.
 */
@Database(entities = [ConversationEntity::class], version = 2, exportSchema = true)
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

    /** No destructive fallback: a missing migration must fail loudly in testing, not drop stars. */
    @Provides
    @Singleton
    fun userDatabase(@ApplicationContext context: Context): UserDatabase =
        Room.databaseBuilder(context, UserDatabase::class.java, "user.db").build()

    @Provides
    fun starredDao(database: UserDatabase): StarredDao = database.starred()

    @Provides
    fun scheduledDao(database: UserDatabase): ScheduledDao = database.scheduled()

    @Provides
    fun conversationPrefsDao(database: UserDatabase): ConversationPrefsDao = database.conversationPrefs()
}
