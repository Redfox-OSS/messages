package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.ui.R
import dev.redfox.obsidian.components.CountBadge
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter

/**
 * Inbox row (PLAN.md §7.3): avatar, name, snippet, time and unread badge. Unread rows get a
 * SemiBold name, primary-coloured snippet and an accent time, like Google Messages.
 */
@Composable
fun ConversationRow(
    conversation: Conversation,
    timeLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val unread = conversation.isUnread

    Row(
        modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.985f, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = ObSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(conversation.participants)
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
