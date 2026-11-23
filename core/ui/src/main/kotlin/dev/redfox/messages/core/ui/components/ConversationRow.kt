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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import dev.redfox.bedrock.components.BrBadge
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.ui.R

/**
 * Inbox row (PLAN.md §7.3): avatar, name, snippet, time and unread badge. Unread rows get a
 * SemiBold name, primary-coloured snippet and a soft accent time, like Google Messages.
 *
 * [selected]: picked in the inbox's selection mode; the avatar eases into an accent check and the
 * row takes a quiet accent tint.
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
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val unread = conversation.isUnread
    val tint by animateColorAsState(
        if (selected) colors.accentContainer else colors.accentContainer.copy(alpha = 0f),
        BrMotion.quick(),
        label = "selectedTint",
    )
    val check by animateFloatAsState(if (selected) 1f else 0f, BrMotion.settle(), label = "selectedCheck")

    Row(
        modifier
            .fillMaxWidth()
            .background(tint)
            .semantics { this.selected = selected }
            .pressable(pressedScale = 0.99f, onLongClick = onLongClick, onClick = onClick)
            .padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Avatar(
                conversation.participants,
                modifier = Modifier.graphicsLayer { alpha = 1f - check },
            )
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        // Eases in from slightly smaller while fading: present, not popped.
                        val s = 0.8f + 0.2f * check
                        scaleX = s
                        scaleY = s
                        alpha = check
                    }
                    .clip(CircleShape)
                    .background(colors.accentMain),
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
        Spacer(Modifier.width(BrSpacing.L))
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
                Spacer(Modifier.width(BrSpacing.S))
                if (conversation.isPinned) {
                    Icon(
                        painterResource(R.drawable.ic_keep),
                        contentDescription = "Pinned",
                        tint = colors.textTertiary,
                        modifier = Modifier.padding(end = BrSpacing.XS).size(14.dp),
                    )
                }
                Text(
                    timeLabel,
                    // Tabular figures at caption size, so times line up down the list.
                    style = type.figure.copy(fontSize = type.caption.fontSize, lineHeight = type.caption.lineHeight),
                    color = if (unread) colors.accentSoft else colors.textTertiary,
                )
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(BrSpacing.S)) {
                Text(
                    conversation.snippet.replace('\n', ' ').ifBlank { if (conversation.hasAttachment) "Attachment" else "" },
                    style = type.body,
                    color = if (unread) colors.textPrimary else colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (unread) BrBadge(conversation.unreadCount)
            }
        }
    }
}
