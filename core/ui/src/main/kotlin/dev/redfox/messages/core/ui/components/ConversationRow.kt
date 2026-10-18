package dev.redfox.messages.core.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.ui.R
import dev.redfox.obsidian.components.CountBadge
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.ObMotion
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter

/**
 * Inbox row (PLAN.md §7.3): avatar, name, snippet, time and unread badge. Unread rows get a
 * SemiBold name, primary-coloured snippet and an accent time, like Google Messages.
 *
 * [selected]: picked in the inbox's selection mode; the avatar turns into an accent check and the
 * row takes an accent tint.
 */
@Composable
fun ConversationRow(
    conversation: Conversation,
    timeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val unread = conversation.isUnread
    val tint by animateColorAsState(
        if (selected) colors.accentContainer else colors.accentContainer.copy(alpha = 0f),
        ObMotion.snappy(),
        label = "selectedTint",
    )
    val check by animateFloatAsState(if (selected) 1f else 0f, ObMotion.bouncy(), label = "selectedCheck")

    Row(
        modifier
            .fillMaxWidth()
            .background(tint)
            .semantics { this.selected = selected }
            .pressable(pressedScale = 0.985f, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = ObSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(
                conversation.participants,
                modifier = Modifier.graphicsLayer { alpha = 1f - check.coerceIn(0f, 1f) },
            )
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = check
                        scaleY = check
                        alpha = check.coerceIn(0f, 1f)
                    }
                    .clip(CircleShape)
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painterResource(R.drawable.ic_check),
                    contentDescription = null,
                    tint = colors.onAccent,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Spacer(Modifier.width(ObSpacing.L))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    conversation.title,
                    style = type.bodyLarge.copy(fontWeight = if (unread) FontWeight.SemiBold else FontWeight.Medium),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(ObSpacing.S))
                if (conversation.isPinned) {
                    Icon(
                        painterResource(R.drawable.ic_keep),
                        contentDescription = "Pinned",
                        tint = colors.textTertiary,
                        modifier = Modifier.padding(end = ObSpacing.XXS).size(14.dp),
                    )
                }
                Text(
                    timeLabel,
                    style = type.label,
                    color = if (unread) colors.accentLight else colors.textTertiary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(ObSpacing.S)) {
                Text(
                    conversation.snippet.replace('\n', ' ').ifBlank { if (conversation.hasAttachment) "Attachment" else "" },
                    style = type.body,
                    color = if (unread) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) CountBadge(conversation.unreadCount)
            }
        }
    }
}
