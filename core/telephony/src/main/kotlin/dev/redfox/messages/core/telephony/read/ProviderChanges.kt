package dev.redfox.messages.core.telephony.read

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.onStart
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Ticks once on collection and again whenever anything in the SMS/MMS store changes.
 * Bursts (a 10-part SMS, a bulk delete in another app) collapse into one reload.
 */
@Singleton
class ProviderChanges @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    @OptIn(FlowPreview::class)
    val ticks: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        // Providers differ in which URI they announce a change on (Samsung notifies MMS updates
        // on content://mms only), so listen on all three.
        WATCHED.forEach { context.contentResolver.registerContentObserver(it, true, observer) }
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }
        .debounce(250)
        .onStart { emit(Unit) }
        .conflate()

    private companion object {
        val WATCHED = listOf("content://mms-sms/".toUri(), "content://mms/".toUri(), "content://sms/".toUri())
    }
}
