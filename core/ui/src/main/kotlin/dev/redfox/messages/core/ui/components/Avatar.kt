package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.ui.R
import dev.redfox.obsidian.components.ObAvatar
import dev.redfox.obsidian.components.ObAvatarPair

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
        ObAvatarPair(
            front = { m, s -> ContactAvatar(participants[0], s, m) },
            back = { m, s -> ContactAvatar(participants[1], s, m) },
            modifier = modifier,
            size = size,
        )
    } else {
        ContactAvatar(participants.firstOrNull(), size, modifier)
    }
}

@Composable
private fun ContactAvatar(contact: Contact?, size: Dp, modifier: Modifier = Modifier) {
    val service = contact?.isAlphanumericSender == true
    val photo = contact?.photoUri
    ObAvatar(
        name = contact?.name?.takeUnless { service },
        modifier = modifier,
        size = size,
        fallbackIcon = if (service) R.drawable.ic_sms else R.drawable.ic_person,
        image = photo?.let {
            {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(size),
                )
            }
        },
    )
}
