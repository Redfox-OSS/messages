package dev.redfox.messages.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import dev.redfox.messages.core.model.OutgoingAttachment
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import dev.redfox.messages.core.model.SimCard
import dev.redfox.messages.core.ui.R
import dev.redfox.obsidian.effects.ObGlows
import dev.redfox.obsidian.effects.glassEffect
import dev.redfox.obsidian.effects.glow
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter

private val ComposerShape = RoundedCornerShape(26.dp)
private val SendSize = 44.dp

/**
 * The message composer (PLAN.md §7.3): a floating glass bar like the nav bar, with the SIM
 * badge (dual-SIM phones only), the text field and the send button. Send is an accent circle
 * that glows only while there's something to send. A segment counter appears above the field
 * once a message gets long.
 */
@Composable
fun ComposerBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    sim: SimCard?,
    showSim: Boolean,
    onSimClick: () -> Unit,
    counter: String?,
    modifier: Modifier = Modifier,
    placeholder: String = "Text message",
    /** Long-press on send, e.g. to schedule. */
    onSendLongPress: (() -> Unit)? = null,
    /** Photos and voice notes waiting to go with the message. */
    attachments: List<OutgoingAttachment> = emptyList(),
    onRemoveAttachment: (OutgoingAttachment) -> Unit = {},
    /** Shows the add-photo button. */
    onAttachClick: (() -> Unit)? = null,
    /** Shows a mic in place of send while there's nothing to send. */
    onMicClick: (() -> Unit)? = null,
    /** Non-null while a voice note is recording: how long so far. */
    recordingMillis: Long? = null,
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter - ObSpacing.XXS)
            .glow(Color.Black, ComposerShape, ObGlows.Elevation)
            .clip(ComposerShape)
            .glassEffect(colors.navScrim)
            .border(1.dp, colors.borderStrong, ComposerShape)
            .padding(ObSpacing.XS),
    ) {
        if (attachments.isNotEmpty()) {
            AttachmentStrip(attachments, onRemoveAttachment)
        }
        if (recordingMillis != null) {
            RecordingRow(recordingMillis, onStop = onStopRecording, onCancel = onCancelRecording)
        } else {
            ComposeRow(
                text = text,
                onTextChange = onTextChange,
                onSend = onSend,
                canSend = canSend,
                sim = sim,
                showSim = showSim,
                onSimClick = onSimClick,
                counter = counter,
                placeholder = placeholder,
                onSendLongPress = onSendLongPress,
                onAttachClick = onAttachClick,
                onMicClick = onMicClick,
            )
        }
    }
}

@Composable
private fun ComposeRow(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    canSend: Boolean,
    sim: SimCard?,
    showSim: Boolean,
    onSimClick: () -> Unit,
    counter: String?,
    placeholder: String,
    onSendLongPress: (() -> Unit)?,
    onAttachClick: (() -> Unit)?,
    onMicClick: (() -> Unit)?,
) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.XS),
    ) {
        if (onAttachClick != null) {
            RoundIconButton(R.drawable.ic_add_photo_alternate, "Add photo", onClick = onAttachClick)
        }
        if (showSim && sim != null) {
            SimBadge(sim, onClick = onSimClick, modifier = Modifier.padding(bottom = 6.dp))
        }
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = SendSize)
                .padding(start = if (showSim || onAttachClick != null) 0.dp else ObSpacing.M),
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(visible = counter != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    counter.orEmpty(),
                    style = type.caption,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = ObSpacing.XXS),
                )
            }
            Box(Modifier.padding(vertical = 10.dp)) {
                if (text.isEmpty()) {
                    Text(placeholder, style = type.body, color = colors.textTertiary)
                }
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = type.body.copy(color = colors.textPrimary),
                    cursorBrush = SolidColor(colors.accent),
                    maxLines = 6,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (!canSend && onMicClick != null) {
            RoundIconButton(R.drawable.ic_mic, "Record voice message", onClick = onMicClick, filled = true)
        } else {
            SendButton(enabled = canSend, onClick = onSend, onLongClick = onSendLongPress)
        }
    }
}

/** While recording: a pulsing dot and the time, cancel, and stop (which adds the note to the message). */
@Composable
private fun RecordingRow(elapsedMillis: Long, onStop: () -> Unit, onCancel: () -> Unit) {
    val colors = ObsidianTheme.colors
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "recDot",
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = SendSize),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.M),
    ) {
        RoundIconButton(R.drawable.ic_close, "Cancel recording", onClick = onCancel)
        Box(Modifier.size(10.dp).alpha(pulse).clip(CircleShape).background(colors.error))
        Text(formatDuration(elapsedMillis), style = ObsidianTheme.typography.bodyLarge, color = colors.textPrimary, modifier = Modifier.weight(1f))
        RoundIconButton(R.drawable.ic_stop, "Stop recording", onClick = onStop, filled = true)
    }
}

/** Thumbnails and voice-note chips above the field, each removable. */
@Composable
private fun AttachmentStrip(attachments: List<OutgoingAttachment>, onRemove: (OutgoingAttachment) -> Unit) {
    val colors = ObsidianTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(ObSpacing.XS),
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.S),
    ) {
        attachments.forEach { attachment ->
            Box {
                if (attachment.isImage) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = "Photo to send",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(RoundedCornerShape(14.dp)).background(colors.tile),
                    )
                } else {
                    Box(
                        Modifier.clip(RoundedCornerShape(14.dp)).background(colors.tile).padding(horizontal = ObSpacing.S),
                    ) { AudioPlayerView(attachment.uri) }
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(ObSpacing.XXS)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f))
                        .pressable { onRemove(attachment) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove", tint = Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: Int, description: String, onClick: () -> Unit, filled: Boolean = false) {
    val colors = ObsidianTheme.colors
    Box(
        Modifier
            .size(SendSize)
            .then(if (filled) Modifier.glow(colors.accent, CircleShape, ObGlows.Action) else Modifier)
            .clip(CircleShape)
            .background(if (filled) colors.accent else Color.Transparent)
            .pressable(pressedScale = 0.93f, haptic = HapticFeedbackType.ContextClick, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painterResource(icon),
            contentDescription = description,
            tint = if (filled) colors.onAccent else colors.textSecondary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val colors = ObsidianTheme.colors
    val fill by animateColorAsState(if (enabled) colors.accent else colors.tile, label = "sendFill")
    val tint by animateColorAsState(if (enabled) colors.onAccent else colors.textDisabled, label = "sendTint")
    Box(
        Modifier
            .size(SendSize)
            .then(if (enabled) Modifier.glow(colors.accent, CircleShape, ObGlows.Action) else Modifier)
            .clip(CircleShape)
            .background(fill)
            .pressable(
                enabled = enabled,
                pressedScale = 0.93f,
                haptic = HapticFeedbackType.Confirm,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_send), contentDescription = "Send. Long-press to schedule.", tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** "SIM 1" / "SIM 2" pill; tapping switches SIM. */
@Composable
private fun SimBadge(sim: SimCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    Row(
        modifier
            .clip(CircleShape)
            .background(colors.tile)
            .border(1.dp, colors.border, CircleShape)
            .pressable(haptic = HapticFeedbackType.SegmentTick, onClick = onClick)
            .padding(horizontal = ObSpacing.S, vertical = ObSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.XXS),
    ) {
        Icon(
            painterResource(R.drawable.ic_sim_card),
            contentDescription = "Sending from ${sim.label}. Tap to switch SIM.",
            tint = colors.accentLight,
            modifier = Modifier.size(16.dp),
        )
        Text("${sim.slotIndex + 1}", style = ObsidianTheme.typography.labelStrong, color = colors.textPrimary)
    }
}

/** A short notice in place of the composer when replying isn't possible here (e.g. group MMS). */
@Composable
fun ComposerNotice(text: String, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    Box(
        modifier
            .clip(CircleShape)
            .glassEffect(colors.navScrim)
            .background(colors.border)
            .padding(horizontal = ObSpacing.L, vertical = ObSpacing.S),
    ) {
        Text(text, style = ObsidianTheme.typography.label, color = colors.textSecondary)
    }
}
