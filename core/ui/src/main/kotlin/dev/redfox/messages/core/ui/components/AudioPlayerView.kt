package dev.redfox.messages.core.ui.components

import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import dev.redfox.messages.core.ui.R
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import kotlinx.coroutines.delay

/**
 * A voice note in a bubble (or the composer): play / pause, a progress track and the time.
 * The player is created on first play and released when the view leaves the screen.
 */
@Composable
fun AudioPlayerView(uri: String, modifier: Modifier = Modifier, accent: Color = ObsidianTheme.colors.accentLight) {
    val context = LocalContext.current
    val colors = ObsidianTheme.colors
    var player by remember(uri) { mutableStateOf<MediaPlayer?>(null) }
    var playing by remember(uri) { mutableStateOf(false) }
    var positionMs by remember(uri) { mutableIntStateOf(0) }
    var durationMs by remember(uri) { mutableIntStateOf(0) }

    DisposableEffect(uri) {
        onDispose {
            player?.release()
            player = null
        }
    }
    // Read the length up front so the label shows before playing.
    LaunchedEffect(uri) {
        durationMs = runCatching {
            android.media.MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri.toUri())
                retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toIntOrNull() ?: 0
            }
        }.getOrDefault(0)
    }
    LaunchedEffect(playing) {
        while (playing) {
            positionMs = player?.currentPosition ?: 0
            delay(100)
        }
    }

    fun toggle() {
        val current = player ?: runCatching {
            MediaPlayer().apply {
                setDataSource(context, uri.toUri())
                setOnCompletionListener { playing = false; positionMs = 0 }
                prepare()
                durationMs = duration
            }
        }.getOrNull()?.also { player = it } ?: return
        if (current.isPlaying) {
            current.pause()
            playing = false
        } else {
            current.start()
            playing = true
        }
    }

    Row(
        modifier.padding(vertical = ObSpacing.XXS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.M),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.18f))
                .pressable(haptic = HapticFeedbackType.ContextClick, onClick = ::toggle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow),
                contentDescription = if (playing) "Pause voice message" else "Play voice message",
                tint = accent,
            )
        }
        Column(Modifier.width(150.dp), verticalArrangement = Arrangement.spacedBy(ObSpacing.XS)) {
            val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
            Box(Modifier.fillMaxWidth().height(3.dp).clip(CircleShape).background(colors.borderStrong)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(accent))
            }
            Text(
                formatDuration(if (playing || positionMs > 0) positionMs.toLong() else durationMs.toLong()),
                style = ObsidianTheme.typography.caption,
                color = colors.textSecondary,
            )
        }
    }
}

/** "0:07", "1:23". */
fun formatDuration(millis: Long): String {
    val seconds = (millis / 1000).coerceAtLeast(0)
    return "%d:%02d".format(seconds / 60, seconds % 60)
}
