package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import dev.redfox.messages.core.model.DeliveryStatus
import dev.redfox.messages.core.ui.format.BrandLink
import dev.redfox.obsidian.effects.pressable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.redfox.messages.core.model.Attachment
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageBox
import dev.redfox.messages.core.ui.R
import dev.redfox.obsidian.theme.ObShapes
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter

/** Where a bubble sits in a run of consecutive messages from the same side, for corner shaping. */
enum class BubblePosition { Single, First, Middle, Last }

private val Round = 18.dp
private val Tight = 6.dp

/**
 * Message bubble (PLAN.md §7.3). Incoming sits on `card`; outgoing uses Obsidian's "selected"
 * recipe — `accentContainerStrong` + `accentBorder` — rather than a solid accent, so a long
 * thread doesn't become a wall of glow. Grouped runs tighten the corners on their tail side.
 */
@Composable
fun MessageBubble(
    message: Message,
    position: BubblePosition,
    timeLabel: String?,
    showSenderName: Boolean,
    modifier: Modifier = Modifier,
    /** Shown as "Tap to retry" on a failed message when set. */
    onRetry: (() -> Unit)? = null,
    /** Well-known services linked in the message ([dev.redfox.messages.core.ui.format.LinkBrands.find]). */
    brandLinks: List<BrandLink> = emptyList(),
    onOpenBrandLink: (BrandLink) -> Unit = {},
    isStarred: Boolean = false,
    /** Long-press: the message actions sheet (copy, star, delete). */
    onLongPress: (() -> Unit)? = null,
    /** Retry a failed MMS download. */
    onRetryDownload: (() -> Unit)? = null,
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val outgoing = message.isOutgoing
    val failed = message.box == MessageBox.Failed
    val shape = bubbleShape(outgoing, position)

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        val sender = message.sender
        if (showSenderName && sender != null) {
            Text(
                sender.displayName,
                style = type.label,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = ObSpacing.M, bottom = ObSpacing.XXS),
            )
        }
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (onLongPress != null) {
                        Modifier.pressable(pressedScale = 0.97f, haptic = HapticFeedbackType.LongPress, onLongClick = onLongPress, onClick = {})
                    } else Modifier
                )
                .clip(shape)
                .background(if (outgoing) colors.accentContainerStrong else colors.card)
                .border(1.dp, if (failed) colors.error else if (outgoing) colors.accentBorder else colors.border, shape)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(ObSpacing.S),
        ) {
            message.subject?.let {
                Text(it, style = type.labelStrong, color = colors.textPrimary)
            }
            message.attachments.forEach { AttachmentView(it) }
            when {
                message.isPendingDownload -> PendingDownload(message.isDownloadFailed, onRetryDownload)
                // No SelectionContainer: one per bubble is costly while flinging. Copy comes from the
                // long-press message sheet (PLAN.md §8.4).
                message.body.isNotEmpty() -> LinkifiedText(message.body, style = type.body, color = colors.textPrimary)
            }
        }
        if (brandLinks.isNotEmpty()) {
            BrandLinkButtons(
                links = brandLinks,
                onOpen = onOpenBrandLink,
                horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
                modifier = Modifier.padding(top = ObSpacing.XS),
            )
        }
        val sending = message.box == MessageBox.Outbox || message.box == MessageBox.Queued
        if (timeLabel != null || failed || sending || isStarred) {
            val retry = if (failed && onRetry != null) {
                Modifier.pressable(haptic = HapticFeedbackType.ContextClick, onClick = onRetry)
            } else Modifier
            Row(
                retry.padding(top = ObSpacing.XXS, start = ObSpacing.S, end = ObSpacing.S),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ObSpacing.XXS),
            ) {
                if (isStarred) {
                    Icon(
                        painterResource(R.drawable.ic_star_filled),
                        contentDescription = "Starred",
                        tint = colors.accentLight,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (failed) {
                    Icon(
                        painterResource(R.drawable.ic_error),
                        contentDescription = null,
                        tint = colors.error,
                        modifier = Modifier.size(14.dp),
                    )
                }
                Text(
                    statusCaption(message, timeLabel, canRetry = onRetry != null),
                    style = type.caption,
                    color = if (failed) colors.error else colors.textTertiary,
                )
            }
        }
    }
}

/** "Sending…", "Not sent · Tap to retry", "10:42 · Delivered", or just the time. */
private fun statusCaption(message: Message, timeLabel: String?, canRetry: Boolean): String = when {
    message.box == MessageBox.Failed -> if (canRetry) "Not sent · Tap to retry" else "Not sent"
    message.box == MessageBox.Outbox || message.box == MessageBox.Queued -> "Sending…"
    message.isOutgoing && message.deliveryStatus == DeliveryStatus.Delivered ->
        listOfNotNull(timeLabel, "Delivered").joinToString(" · ")
    else -> timeLabel.orEmpty()
}

/** An MMS still on the carrier's server: downloading, or failed with a retry. */
@Composable
private fun PendingDownload(failed: Boolean, onRetry: (() -> Unit)?) {
    val colors = ObsidianTheme.colors
    Row(
        if (failed && onRetry != null) Modifier.pressable(haptic = HapticFeedbackType.ContextClick, onClick = onRetry) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.S),
    ) {
        Icon(
            painterResource(R.drawable.ic_download),
            contentDescription = null,
            tint = if (failed) colors.error else colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            if (failed) "Couldn't download · Tap to retry" else "Downloading multimedia message…",
            style = ObsidianTheme.typography.body,
            color = if (failed) colors.error else colors.textSecondary,
        )
    }
}

@Composable
private fun AttachmentView(attachment: Attachment) {
    val colors = ObsidianTheme.colors
    if (attachment.isAudio) {
        AudioPlayerView(attachment.uri)
        return
    }
    if (attachment.isImage) {
        AsyncImage(
            model = attachment.uri,
            contentDescription = attachment.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .widthIn(max = 272.dp)
                .heightIn(max = 320.dp)
                .clip(ObShapes.Small)
                .background(colors.tile),
        )
    } else {
        Row(
            Modifier
                .clip(ObShapes.Small)
                .background(colors.tile)
                .padding(horizontal = ObSpacing.M, vertical = ObSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(ObSpacing.S),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_attach_file), contentDescription = null, tint = colors.textSecondary)
            }
            Text(
                attachment.name ?: attachment.mimeType,
                style = ObsidianTheme.typography.label,
                color = colors.textPrimary,
                maxLines = 1,
            )
        }
    }
}

private fun bubbleShape(outgoing: Boolean, position: BubblePosition): RoundedCornerShape {
    // Tail side = the side the bubble is aligned to; tighten corners that touch a neighbour.
    val top = position == BubblePosition.Middle || position == BubblePosition.Last
    val bottom = position == BubblePosition.First || position == BubblePosition.Middle
    return if (outgoing) {
        RoundedCornerShape(Round, if (top) Tight else Round, if (bottom) Tight else Round, Round)
    } else {
        RoundedCornerShape(if (top) Tight else Round, Round, Round, if (bottom) Tight else Round)
    }
}

/** Centred day separator ("TODAY", "MON, 13 OCT"). */
@Composable
fun DateDivider(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = ObSpacing.M), contentAlignment = Alignment.Center) {
        Text(
            label.uppercase(),
            style = ObsidianTheme.typography.caption,
            color = ObsidianTheme.colors.textTertiary,
        )
    }
}
