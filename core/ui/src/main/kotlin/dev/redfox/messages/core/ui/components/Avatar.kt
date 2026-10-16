package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.ui.R
import dev.redfox.obsidian.theme.ObsidianTheme

/**
 * Contact photo, or initials on a neutral tile. Service senders ("VM-HDFCBK") get a glyph,
 * since their "initials" are meaningless. Groups show two overlapping avatars.
 */
@Composable
fun Avatar(
    participants: List<Contact>,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    if (participants.size > 1) {
        val small = size * 0.72f
        Box(modifier.size(size)) {
            SingleAvatar(participants[1], small, Modifier.align(Alignment.TopEnd))
            SingleAvatar(
                participants[0],
                small,
                Modifier
                    .align(Alignment.BottomStart)
                    // A background-coloured ring separates the two circles.
                    .border(2.dp, ObsidianTheme.colors.background, CircleShape)
            )
        }
    } else {
        SingleAvatar(participants.firstOrNull(), size, modifier)
    }
}

@Composable
private fun SingleAvatar(contact: Contact?, size: Dp, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.tile),
        contentAlignment = Alignment.Center
    ) {
        val photo = contact?.photoUri
        val name = contact?.name
        when {
            photo != null -> AsyncImage(
                model = photo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
            name == null || contact.isAlphanumericSender -> Icon(
                painterResource(if (contact?.isAlphanumericSender == true) R.drawable.ic_sms else R.drawable.ic_person),
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier.size(size * 0.5f),
            )
            else -> Text(
                initials(name),
                style = ObsidianTheme.typography.labelStrong.copy(fontSize = (size.value * 0.36f).sp),
                color = colors.textSecondary,
                modifier = Modifier.offset(y = (-0.5).dp),
            )
        }
    }
}

private fun initials(name: String): String =
    name.split(' ', '-', '.')
        .filter { it.isNotBlank() && it.first().isLetterOrDigit() }
        .take(2)
        .joinToString("") { it.first().uppercase() }
        .ifEmpty { name.take(1).uppercase() }
