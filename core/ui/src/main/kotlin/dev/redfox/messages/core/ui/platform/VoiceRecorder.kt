package dev.redfox.messages.core.ui.platform

import android.content.Context
import android.media.MediaRecorder
import android.net.Uri
import android.os.SystemClock
import android.util.Log
import java.io.File

/** A finished voice note: the file, its MIME type and how long it is. */
data class VoiceNote(val uri: Uri, val mimeType: String, val durationMillis: Long)

/**
 * Records a voice note for MMS: AAC in an MPEG-4 (.m4a) file, mono at 16 kHz / 24 kbps (about
 * 3 KB a second, clear for speech), stopped automatically before it outgrows the carrier's
 * MMS limit. Needs RECORD_AUDIO.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    /** Milliseconds since recording started. */
    val elapsedMillis: Long get() = if (recorder == null) 0 else SystemClock.elapsedRealtime() - startedAt

    /**
     * Starts recording. [onLimitReached] runs when the size cap stops it (call [stop] then).
     * @return false if the microphone couldn't be opened.
     */
    fun start(maxBytes: Int, onLimitReached: () -> Unit): Boolean {
        cancel()
        val output = File(context.cacheDir, "voice").apply { mkdirs() }.let { File(it, "voice_${System.currentTimeMillis()}.m4a") }
        val media = MediaRecorder(context)
        return try {
            media.setAudioSource(MediaRecorder.AudioSource.MIC)
            media.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            media.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            media.setAudioChannels(1)
            media.setAudioSamplingRate(SAMPLE_RATE)
            media.setAudioEncodingBitRate(BIT_RATE)
            media.setMaxFileSize(maxBytes.toLong())
            media.setOnInfoListener { _, what, _ ->
                if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_FILESIZE_REACHED) onLimitReached()
            }
            media.setOutputFile(output)
            media.prepare()
            media.start()
            recorder = media
            file = output
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't start recording", e)
            media.release()
            output.delete()
            false
        }
    }

    /** Stops and returns the note, or null if it was too short to keep (an accidental tap). */
    fun stop(): VoiceNote? {
        val media = recorder ?: return null
        val output = file
        val duration = elapsedMillis
        recorder = null
        file = null
        val ok = runCatching { media.stop() }.isSuccess // Throws when stopped too soon after start.
        media.release()
        if (!ok || output == null || duration < MIN_DURATION_MILLIS) {
            output?.delete()
            return null
        }
        return VoiceNote(Uri.fromFile(output), MIME_TYPE, duration)
    }

    fun cancel() {
        val media = recorder ?: return
        recorder = null
        runCatching { media.stop() }
        media.release()
        file?.delete()
        file = null
    }

    companion object {
        private const val TAG = "VoiceRecorder"
        const val MIME_TYPE = "audio/mp4"
        private const val SAMPLE_RATE = 16_000
        private const val BIT_RATE = 24_000
        private const val MIN_DURATION_MILLIS = 600L
    }
}
