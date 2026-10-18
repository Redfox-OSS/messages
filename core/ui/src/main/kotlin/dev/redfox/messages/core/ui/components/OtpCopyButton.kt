package dev.redfox.messages.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import dev.redfox.messages.core.ui.R
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import kotlinx.coroutines.delay

/** How long the button says "Copied" before showing the code again. */
private const val COPIED_MILLIS = 1800L

/**
 * "Copy 482913" under a message carrying a one-time code: one tap copies just the code (no
 * long-press, no selecting digits out of the text), then it confirms with "Copied".
 */
@Composable
fun OtpCopyButton(code: String, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    var copied by remember(code) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(COPIED_MILLIS)
            copied = false
        }
    }
    Row(
        modifier
            .semantics { contentDescription = if (copied) "Code copied" else "Copy code $code" }
            .pressable(pressedScale = 0.96f, haptic = HapticFeedbackType.Confirm) {
                onCopy(code)
                copied = true
            }
            .clip(CircleShape)
            .background(colors.card)
            .border(1.dp, if (copied) colors.accentBorder else colors.borderStrong, CircleShape)
            .padding(horizontal = ObSpacing.M, vertical = ObSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.S),
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
            label = "otpCopied",
        ) { done ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
                Icon(
                    painterResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_content_copy),
                    contentDescription = null,
                    tint = colors.accentLight,
                    modifier = Modifier.size(16.dp),
                )
                if (done) {
                    Text("Copied", style = ObsidianTheme.typography.labelStrong, color = colors.textPrimary)
                } else {
                    Text("Copy", style = ObsidianTheme.typography.labelStrong, color = colors.textSecondary)
                    Text(code, style = ObsidianTheme.typography.labelStrong, color = colors.textPrimary)
                }
            }
        }
    }
}
