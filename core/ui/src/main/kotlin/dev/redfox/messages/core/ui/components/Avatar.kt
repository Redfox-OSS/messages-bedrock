package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.redfox.bedrock.components.BrAvatar
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.ui.R

/**
 * Contact photo, or initials on a soft tint chosen from the name. Service senders ("VM-HDFCBK")
 * and unsaved numbers get a glyph, since their "initials" are meaningless. Groups show two
 * smaller overlapping avatars.
 */
@Composable
fun Avatar(
    participants: List<Contact>,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    if (participants.size > 1) {
        AvatarPair(participants[0], participants[1], modifier, size)
    } else {
        ContactAvatar(participants.firstOrNull(), size, modifier)
    }
}

/**
 * Two avatars in the space of one: the second at the top end, the first at the bottom start,
 * ringed in the page background so the circles read as separate.
 */
@Composable
private fun AvatarPair(front: Contact, back: Contact, modifier: Modifier, size: Dp) {
    val small = size * 0.72f
    Box(modifier.size(size)) {
        ContactAvatar(back, small, Modifier.align(Alignment.TopEnd))
        ContactAvatar(
            front,
            small,
            Modifier
                .align(Alignment.BottomStart)
                .border(2.dp, BedrockTheme.colors.background, CircleShape),
        )
    }
}

@Composable
private fun ContactAvatar(contact: Contact?, size: Dp, modifier: Modifier = Modifier) {
    val colors = BedrockTheme.colors
    val service = contact?.isAlphanumericSender == true
    val name = contact?.name?.takeUnless { service }
    val photo = contact?.photoUri
    // No usable initials (a service sender, an unsaved number, punctuation only): show a glyph.
    val hasInitials = name != null && hasLetterInitials(name)
    BrAvatar(
        // The name picks the tint, so key it on something stable even without a contact name.
        name = name ?: contact?.address.orEmpty(),
        modifier = modifier,
        size = size,
        image = when {
            photo != null -> {
                {
                    AsyncImage(
                        model = photo,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(size),
                    )
                }
            }
            !hasInitials -> {
                {
                    Icon(
                        painterResource(if (service) R.drawable.ic_sms else R.drawable.ic_person),
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(size * 0.5f),
                    )
                }
            }
            else -> null
        },
    )
}

/** Whether [name] has a word starting with a letter or digit, so its initials mean something. */
private fun hasLetterInitials(name: String): Boolean =
    name.split(' ', '-', '.').any { it.isNotBlank() && it.first().isLetterOrDigit() }
