package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import dev.redfox.obsidian.motion.RollingText
import dev.redfox.obsidian.theme.ObsidianTheme

/** Accent pill with an odometer count. No glow: badges repeat down the list (one glow per region). */
@Composable
fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    Box(
        modifier
            .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
            .clip(CircleShape)
            .background(colors.accent)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        RollingText(
            text = if (count > 99) "99+" else count.toString(),
            style = ObsidianTheme.typography.caption,
            color = colors.onAccent,
        )
    }
}
