package dev.redfox.messages.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import kotlin.math.roundToInt

private val TrackHeight = 3.dp

/**
 * A slim, non-blocking sync indicator: a hairline track with an accent fill that eases toward
 * the real [progress], a soft light sweeping along the fill so it reads as alive, and a small
 * caption. It floats over the list (no layout shift) and slides away when done.
 */
@Composable
fun SyncProgressBar(
    visible: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
    label: String = "Syncing messages",
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(260)) { -it / 2 },
        exit = fadeOut(tween(420, delayMillis = 250)) + slideOutVertically(tween(420, delayMillis = 250)) { -it / 2 },
    ) {
        val colors = ObsidianTheme.colors
        val shown by animateFloatAsState(
            targetValue = progress.coerceIn(0f, 1f),
            animationSpec = spring(dampingRatio = 1f, stiffness = 60f),
            label = "syncProgress",
        )
        val sweep by rememberInfiniteTransition(label = "syncSweep").animateFloat(
            initialValue = -0.3f,
            targetValue = 1.3f,
            animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
            label = "sweep",
        )
        val percent = (shown * 100).roundToInt()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = ScreenGutter, vertical = ObSpacing.XS)
                .semantics {
                    contentDescription = "$label, $percent percent"
                    progressBarRangeInfo = ProgressBarRangeInfo(shown, 0f..1f)
                },
            verticalArrangement = Arrangement.spacedBy(ObSpacing.XS),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = ObsidianTheme.typography.caption, color = colors.textTertiary)
                Text("$percent%", style = ObsidianTheme.typography.caption, color = colors.textTertiary)
            }
            val track = colors.tile
            val accent = colors.accent
            val accentLight = colors.accentLight
            Canvas(Modifier.fillMaxWidth().height(TrackHeight)) {
                val radius = CornerRadius(size.height / 2)
                drawRoundRect(track, cornerRadius = radius)
                val fillWidth = size.width * shown
                if (fillWidth <= 0f) return@Canvas
                // Soft halo under the fill, then the fill itself.
                drawRoundRect(
                    accent.copy(alpha = 0.25f),
                    topLeft = Offset(0f, -size.height),
                    size = Size(fillWidth, size.height * 3),
                    cornerRadius = CornerRadius(size.height * 1.5f),
                )
                drawRoundRect(
                    Brush.horizontalGradient(listOf(accent.copy(alpha = 0.75f), accentLight), endX = fillWidth),
                    size = Size(fillWidth, size.height),
                    cornerRadius = radius,
                )
                // The sweep: a short bright band travelling along the filled part only.
                val bandCenter = sweep * fillWidth
                val bandHalf = (size.width * 0.12f).coerceAtLeast(24f)
                drawRoundRect(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, Color.White.copy(alpha = 0.55f), Color.Transparent),
                        startX = bandCenter - bandHalf,
                        endX = bandCenter + bandHalf,
                    ),
                    size = Size(fillWidth, size.height),
                    cornerRadius = radius,
                )
            }
        }
    }
}
