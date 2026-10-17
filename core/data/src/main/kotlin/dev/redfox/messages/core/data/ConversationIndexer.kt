package dev.redfox.messages.core.data

import android.util.Log
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.common.Dispatcher
import dev.redfox.messages.core.common.MessagesDispatcher
import dev.redfox.messages.core.contacts.ContactResolver
import dev.redfox.messages.core.database.ConversationDao
import dev.redfox.messages.core.database.ConversationEntity
import dev.redfox.messages.core.database.ConversationPrefsDao
import dev.redfox.messages.core.database.ConversationPrefsEntity
import dev.redfox.messages.core.domain.MessageCategorizer
import dev.redfox.messages.core.model.SyncStatus
import dev.redfox.messages.core.telephony.read.ProviderChanges
import dev.redfox.messages.core.telephony.read.TelephonyReader
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.measureTimedValue

/**
 * Keeps the Room inbox index in step with the system SMS/MMS store. Runs off the main thread
 * for the life of the process once started; every store change triggers one (conflated)
 * re-scan, and only rows that actually changed are written, so the paged list stays still
 * unless something moved.
 */
@Singleton
internal class ConversationIndexer @Inject constructor(
    private val reader: TelephonyReader,
    private val contacts: ContactResolver,
    private val changes: ProviderChanges,
    private val dao: ConversationDao,
    private val prefsDao: ConversationPrefsDao,
    @param:ApplicationScope private val scope: CoroutineScope,
    @param:Dispatcher(MessagesDispatcher.IO) private val io: CoroutineDispatcher,
) {
    private val _status = MutableStateFlow(SyncStatus())
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var job: Job? = null

    /** Idempotent. Call once READ_SMS is granted. */
    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch(io) { changes.ticks.collect { syncOnce() } }
    }

    private suspend fun syncOnce() {
        _status.update { it.copy(isSyncing = true) }
        try {
            val prefs = prefsDao.all().associateBy { it.threadId }
            val (snapshot, took) = measureTimedValue { buildSnapshot(prefs) }
            val wrote = dao.sync(snapshot)
            Log.d(TAG, "Indexed ${snapshot.size} conversations in $took (changed=$wrote)")
            _status.update { SyncStatus(isSyncing = false, hasCompletedOnce = true) }
        } catch (e: SecurityException) {
            Log.w(TAG, "SMS store not readable: ${e.message}")
            _status.update { it.copy(isSyncing = false) }
        } catch (e: RuntimeException) {
            // An OEM provider quirk must never crash the app; keep the last good index.
            Log.e(TAG, "Indexing failed", e)
            _status.update { it.copy(isSyncing = false, hasCompletedOnce = true) }
        }
    }

    private fun buildSnapshot(prefs: Map<Long, ConversationPrefsEntity>): List<ConversationEntity> {
        val threads = reader.readThreads()
        val people = contacts.resolveAll(threads.flatMap { it.addresses })
        return threads.map { t ->
            // One person can appear as "+919876543210" and "09876543210" in the same thread.
            val participants = t.addresses.mapNotNull(people::get).distinctBy { it.address.matchKey() }
            ConversationEntity(
                threadId = t.threadId,
                participants = ParticipantCodec.encode(participants),
                snippet = t.snippet.toSnippet(),
                timestamp = t.timestamp,
                unreadCount = t.unreadCount,
                messageCount = t.messageCount,
                hasAttachment = t.hasAttachment,
                category = MessageCategorizer.categorize(participants, t.snippet).name,
                pinned = prefs[t.threadId]?.pinned == true,
                archived = prefs[t.threadId]?.archived == true,
            )
        }
    }

    private companion object {
        const val TAG = "ConversationIndexer"
    }
}

/**
 * The inbox shows one line of the latest message, so store only the start of it, on one line.
 * Laying out (and shaping, for Bengali or Hindi) a whole long SMS per row while scrolling
 * just to ellipsize it was the inbox's biggest per-row cost.
 */
internal fun String.toSnippet(): String {
    val oneLine = if (length > SNIPPET_SCAN) substring(0, SNIPPET_SCAN) else this
    val cut = oneLine.replace(Whitespace, " ").trim().take(SNIPPET_MAX)
    // Never end on half an emoji.
    return if (cut.isNotEmpty() && cut.last().isHighSurrogate()) cut.dropLast(1) else cut
}

private val Whitespace = Regex("""\s+""")
private const val SNIPPET_MAX = 120
private const val SNIPPET_SCAN = 400

/** Compares phone numbers by their last 10 digits; alphanumeric senders by case-folded text. */
internal fun String.matchKey(): String {
    val digits = filter(Char::isDigit)
    return if (digits.length >= 7 && none(Char::isLetter)) digits.takeLast(10) else lowercase()
}
