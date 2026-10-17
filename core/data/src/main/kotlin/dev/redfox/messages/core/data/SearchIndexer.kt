package dev.redfox.messages.core.data

import android.util.Log
import dev.redfox.messages.core.database.SearchDao
import dev.redfox.messages.core.database.SearchDocEntity
import dev.redfox.messages.core.database.SearchDocEntity.Companion.KIND_MMS
import dev.redfox.messages.core.database.SearchDocEntity.Companion.KIND_SMS
import dev.redfox.messages.core.database.SearchSyncEntity
import dev.redfox.messages.core.domain.SearchQuery
import dev.redfox.messages.core.telephony.read.MessageText
import dev.redfox.messages.core.telephony.read.MessageTextSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.yield
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.measureTime

/**
 * Keeps the full-text search index in step with the system store, incrementally: each pass
 * reads only messages newer than where the last one stopped, in batches, and drops messages
 * that were deleted. The first pass indexes everything; after that a pass is a few small reads.
 *
 * Runs after each inbox sync (on its IO coroutine), so it never competes with it.
 */
@Singleton
internal class SearchIndexer @Inject constructor(
    private val source: MessageTextSource,
    private val dao: SearchDao,
) {
    private val mutex = Mutex()
    private val _ready = MutableStateFlow(false)

    /** True once both tables have been indexed in full; until then search reads the store directly. */
    val ready: StateFlow<Boolean> = _ready.asStateFlow()

    /** [ready], also right after a restart, before this process's first pass. */
    suspend fun isReady(): Boolean = _ready.value || dao.completeKinds() == 2

    suspend fun sync() = mutex.withLock {
        try {
            val took = measureTime {
                syncSms()
                syncMms()
            }
            _ready.value = dao.completeKinds() == 2
            Log.d(TAG, "Search index in step in $took")
        } catch (e: SecurityException) {
            Log.w(TAG, "SMS store not readable: ${e.message}")
        } catch (e: RuntimeException) {
            // A provider quirk must never crash the app; search falls back to the store.
            Log.e(TAG, "Search indexing failed", e)
        }
    }

    private suspend fun syncSms() {
        val live = source.smsIds()
        var state = dao.syncState(KIND_SMS) ?: SearchSyncEntity(KIND_SMS, watermark = 0, complete = false)
        // The store was replaced (restored, or cleared by another app): ids start again, so start again.
        if (live.isNotEmpty() && live.last() < state.watermark || live.isEmpty() && state.watermark > 0) {
            dao.clear(KIND_SMS)
            state = SearchSyncEntity(KIND_SMS, watermark = 0, complete = false)
        }
        removeDeleted(KIND_SMS, live)
        while (true) {
            val batch = source.smsAfter(state.watermark, BATCH)
            val done = batch.size < BATCH
            state = SearchSyncEntity(KIND_SMS, watermark = batch.lastOrNull()?.id ?: state.watermark, complete = done)
            dao.write(batch.map { it.toDoc(KIND_SMS) }, state)
            if (done) break
            yield()
        }
    }

    private suspend fun syncMms() {
        var state = dao.syncState(KIND_MMS) ?: SearchSyncEntity(KIND_MMS, watermark = 0, complete = false)
        if (source.maxMmsTextPartId() < state.watermark) {
            dao.clear(KIND_MMS)
            state = SearchSyncEntity(KIND_MMS, watermark = 0, complete = false)
        }
        removeDeleted(KIND_MMS, source.mmsIds())
        val changes = source.mmsTextChangesAfter(state.watermark)
        changes.messageIds.chunked(BATCH).forEach { chunk ->
            dao.upsert(source.mms(chunk).map { it.toDoc(KIND_MMS) })
            yield()
        }
        dao.setSyncState(SearchSyncEntity(KIND_MMS, watermark = changes.maxPartId, complete = true))
    }

    /** Drops indexed messages no longer in the store ([live] is ascending). */
    private suspend fun removeDeleted(kind: Int, live: LongArray) {
        val gone = dao.messageIds(kind).filter { live.binarySearch(it) < 0 }
        gone.chunked(DELETE_CHUNK).forEach { ids -> dao.delete(ids.map { SearchDocEntity.docId(kind, it) }) }
    }

    private fun MessageText.toDoc(kind: Int) = SearchDocEntity(
        docId = SearchDocEntity.docId(kind, id),
        kind = kind,
        messageId = id,
        threadId = threadId,
        timestamp = timestamp,
        outgoing = outgoing,
        address = address,
        body = body,
        numbers = SearchQuery.indexedNumbers(body),
    )

    private companion object {
        const val TAG = "SearchIndexer"
        /** Rows per read and per transaction: big enough to be quick, small enough to stay light. */
        const val BATCH = 500
        /** Well under SQLite's bound-variable limit. */
        const val DELETE_CHUNK = 500
    }
}
