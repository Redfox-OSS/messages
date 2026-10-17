package dev.redfox.messages.core.telephony.mms

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.redfox.messages.core.model.OutgoingAttachment
import java.io.ByteArrayOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Turns picked media into MMS parts that fit the carrier's limit. Photos are re-encoded as
 * JPEG, shrinking quality and then size until they fit their share of the budget; everything
 * else (voice notes, small GIFs, files) is sent as-is. Blocking; call from an IO dispatcher.
 */
@Singleton
class MmsMedia @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    /**
     * @param budget bytes available for all [attachments] together.
     * @throws IllegalArgumentException when a non-photo attachment alone is over the budget.
     */
    fun toParts(attachments: List<OutgoingAttachment>, budget: Int): List<OutgoingPart> {
        val photos = attachments.count { it.isImage && it.mimeType != GIF }
        val fixed = attachments.filterNot { it.isImage && it.mimeType != GIF }.map { it to read(it.uri.toUri()) }
        val fixedBytes = fixed.sumOf { it.second.size }
        require(fixedBytes <= budget) { "Attachment too large for MMS (${fixedBytes / 1024} KB)" }
        val perPhoto = if (photos == 0) 0 else (budget - fixedBytes) / photos
        var photoIndex = 0
        return attachments.mapIndexed { index, attachment ->
            if (attachment.isImage && attachment.mimeType != GIF) {
                OutgoingPart("image/jpeg", compress(attachment.uri.toUri(), perPhoto), "image_${photoIndex++}.jpg")
            } else {
                val data = fixed.first { it.first === attachment }.second
                OutgoingPart(attachment.mimeType, data, attachment.name ?: "${prefix(attachment)}_$index.${extension(attachment)}")
            }
        }
    }

    private fun compress(uri: Uri, budget: Int): ByteArray {
        val source = ImageDecoder.createSource(context.contentResolver, uri)
        var bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            // Phone cameras shoot 12–50 MP; nothing in an MMS needs more than this.
            val longest = max(info.size.width, info.size.height)
            if (longest > MAX_EDGE) {
                val scale = MAX_EDGE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).roundToInt(), (info.size.height * scale).roundToInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        var quality = START_QUALITY
        while (true) {
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                out.toByteArray()
            }
            if (bytes.size <= budget || bitmap.width < MIN_EDGE) return bytes
            if (quality > MIN_QUALITY) {
                quality -= QUALITY_STEP
            } else {
                bitmap = Bitmap.createScaledBitmap(bitmap, (bitmap.width * 0.75f).roundToInt(), (bitmap.height * 0.75f).roundToInt(), true)
                quality = START_QUALITY
            }
        }
    }

    private fun read(uri: Uri): ByteArray =
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Can't read $uri")

    private fun prefix(attachment: OutgoingAttachment) = when {
        attachment.isAudio -> "audio"
        attachment.mimeType.startsWith("video/") -> "video"
        attachment.isImage -> "image"
        else -> "file"
    }

    private fun extension(attachment: OutgoingAttachment) =
        MimeTypeMap.getSingleton().getExtensionFromMimeType(attachment.mimeType) ?: "bin"

    private companion object {
        const val GIF = "image/gif"
        const val MAX_EDGE = 1600
        const val MIN_EDGE = 320
        const val START_QUALITY = 85
        const val MIN_QUALITY = 55
        const val QUALITY_STEP = 10
    }
}
