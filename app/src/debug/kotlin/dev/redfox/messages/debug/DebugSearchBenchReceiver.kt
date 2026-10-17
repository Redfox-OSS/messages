package dev.redfox.messages.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.paging.testing.asSnapshot
import dagger.hilt.android.AndroidEntryPoint
import dev.redfox.messages.core.common.ApplicationScope
import dev.redfox.messages.core.domain.repository.MessagesRepository
import dev.redfox.messages.core.domain.usecase.SearchMessagesUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.time.measureTimedValue

/**
 * Debug builds only: times real searches (people + count, then the first page of messages),
 * five runs each, and logs them under "SearchBench":
 *
 * ```
 * adb shell am broadcast -n dev.redfox.messages.debug/dev.redfox.messages.debug.DebugSearchBenchReceiver --es q "otp,pay,4521"
 * ```
 */
@AndroidEntryPoint
class DebugSearchBenchReceiver : BroadcastReceiver() {

    @Inject lateinit var search: SearchMessagesUseCase
    @Inject lateinit var messages: MessagesRepository
    @Inject @ApplicationScope lateinit var scope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent) {
        val queries = intent.getStringExtra("q").orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)
        // Starts the inbox sync (and the search index after it) if nothing has yet. Until the
        // index is complete these runs time the old path: a scan of the system store.
        messages.startSync()
        val pending = goAsync()
        scope.launch {
            try {
                for (query in queries) {
                    repeat(RUNS) { run ->
                        val (results, peopleTook) = measureTimedValue { search(query) }
                        val (page, pageTook) = measureTimedValue { search.messages(query).asSnapshot { } }
                        Log.d(TAG, "“$query” run ${run + 1}: people+count ${peopleTook} " +
                            "(${results.conversations.size} people, ${results.messageCount} messages), " +
                            "first page ${pageTook} (${page.size} rows)")
                    }
                }
            } catch (e: RuntimeException) {
                Log.e(TAG, "Bench failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "SearchBench"
        const val RUNS = 5
    }
}
