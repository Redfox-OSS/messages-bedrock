package dev.redfox.messages.core.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.motion.BrDurations
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.SwapText
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.model.Attachment
import dev.redfox.messages.core.model.DeliveryStatus
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.MessageBox
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.format.BrandLink
import kotlinx.coroutines.delay

/** Where a bubble sits in a run of consecutive messages from the same side, for corner shaping. */
enum class BubblePosition { Single, First, Middle, Last }

private val Round = 18.dp
private val Tight = 6.dp
private val BubblePadding = 10.dp

/** How long the OTP pill says "Copied" before going back to "Copy". */
private const val CopiedMillis = 1800L

/**
 * Message bubble (PLAN.md §7.3). Incoming sits on `surfaceRaised`; outgoing uses the accent
 * container family (`accentContainerStrong`, primary text) rather than a saturated accent fill, so
 * a long thread stays calm. Grouped runs tighten the corners on their tail side. No borders and no
 * glow: only a failed message gets a critical hairline.
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
    /** The one-time code in the message ([dev.redfox.messages.core.domain.OtpExtractor]), offered as a copy button. */
    otp: String? = null,
    onCopyOtp: (String) -> Unit = {},
) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val outgoing = message.isOutgoing
    val failed = message.box == MessageBox.Failed
    val shape = bubbleShape(outgoing, position)

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BrSpacing.Gutter),
        horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
    ) {
        val sender = message.sender
        if (showSenderName && sender != null) {
            Text(
                sender.displayName,
                style = type.caption,
                color = colors.textTertiary,
                modifier = Modifier.padding(start = BrSpacing.M, bottom = BrSpacing.XXS),
            )
        }
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .then(
                    if (onLongPress != null) {
                        Modifier.pressable(pressedScale = 0.98f, haptic = HapticFeedbackType.LongPress, onLongClick = onLongPress, onClick = {})
                    } else Modifier
                )
                .clip(shape)
                .background(if (outgoing) colors.accentContainerStrong else colors.surfaceRaised)
                .then(if (failed) Modifier.border(1.dp, colors.critical, shape) else Modifier)
                .padding(horizontal = 14.dp, vertical = BubblePadding),
            verticalArrangement = Arrangement.spacedBy(BrSpacing.S),
        ) {
            message.subject?.let {
                Text(it, style = type.label, color = colors.textPrimary)
            }
            message.attachments.forEach { AttachmentView(it) }
            when {
                message.isPendingDownload -> PendingDownload(message.isDownloadFailed, onRetryDownload)
                // No SelectionContainer: one per bubble is costly while flinging. Copy comes from the
                // long-press message sheet (PLAN.md §8.4).
                message.body.isNotEmpty() -> LinkifiedText(message.body, style = type.body, color = colors.textPrimary)
            }
        }
        if (otp != null) {
            OtpCopyPill(value = otp, onCopy = onCopyOtp, modifier = Modifier.padding(top = BrSpacing.XS))
        }
        if (brandLinks.isNotEmpty()) {
            BrandLinkButtons(
                links = brandLinks,
                onOpen = onOpenBrandLink,
                horizontalAlignment = if (outgoing) Alignment.End else Alignment.Start,
                modifier = Modifier.padding(top = BrSpacing.XS),
            )
        }
        val sending = message.box == MessageBox.Outbox || message.box == MessageBox.Queued
        if (timeLabel != null || failed || sending || isStarred) {
            val retry = if (failed && onRetry != null) {
                Modifier.pressable(haptic = HapticFeedbackType.ContextClick, onClick = onRetry)
            } else Modifier
            Row(
                retry.padding(top = BrSpacing.XXS, start = BrSpacing.S, end = BrSpacing.S),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(BrSpacing.XS),
            ) {
                if (isStarred) {
                    Icon(
                        painterResource(R.drawable.ic_star_filled),
                        contentDescription = "Starred",
                        tint = colors.accentSoft,
                        modifier = Modifier.size(12.dp),
                    )
                }
                if (failed) {
                    Icon(
                        painterResource(R.drawable.ic_error),
                        contentDescription = null,
                        tint = colors.critical,
                        modifier = Modifier.size(14.dp),
                    )
                }
                // The caption carries a time, so it uses tabular figures at caption size; status
                // changes ("Sending…" → "10:42 · Delivered") swap calmly in place.
                SwapText(
                    statusCaption(message, timeLabel, canRetry = onRetry != null),
                    style = type.figure.copy(fontSize = type.caption.fontSize, lineHeight = type.caption.lineHeight),
                    color = if (failed) colors.critical else colors.textTertiary,
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

/**
 * The one-time code under a message, as a small tonal pill: the code in tabular figures and a
 * "Copy" label that swaps to "Copied" for a moment after a tap.
 */
@Composable
private fun OtpCopyPill(value: String, onCopy: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    var copied by remember(value) { mutableStateOf(false) }
    LaunchedEffect(copied) {
        if (copied) {
            delay(CopiedMillis)
            copied = false
        }
    }
    Row(
        modifier
            .heightIn(min = 36.dp)
            .semantics { contentDescription = if (copied) "Copied" else "Copy $value" }
            .pressable(pressedScale = 0.94f, haptic = HapticFeedbackType.Confirm) {
                onCopy(value)
                copied = true
            }
            .clip(CircleShape)
            .background(colors.surfaceRaised)
            .padding(horizontal = BrSpacing.M, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
    ) {
        Crossfade(copied, animationSpec = tween(BrDurations.Swap, easing = BrMotion.Calm), label = "copiedIcon") { done ->
            Icon(
                painterResource(if (done) R.drawable.ic_check_circle else R.drawable.ic_content_copy),
                contentDescription = null,
                tint = colors.accentSoft,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(value, style = type.figure, color = colors.textPrimary)
        SwapText(if (copied) "Copied" else "Copy", style = type.labelSmall, color = colors.textSecondary)
    }
}

/** An MMS still on the carrier's server: downloading, or failed with a retry. */
@Composable
private fun PendingDownload(failed: Boolean, onRetry: (() -> Unit)?) {
    val colors = BedrockTheme.colors
    Row(
        if (failed && onRetry != null) Modifier.pressable(haptic = HapticFeedbackType.ContextClick, onClick = onRetry) else Modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
    ) {
        Icon(
            painterResource(R.drawable.ic_download),
            contentDescription = null,
            tint = if (failed) colors.critical else colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            if (failed) "Couldn't download · Tap to retry" else "Downloading multimedia message…",
            style = BedrockTheme.typography.body,
            color = if (failed) colors.critical else colors.textSecondary,
        )
    }
}

@Composable
private fun AttachmentView(attachment: Attachment) {
    val colors = BedrockTheme.colors
    // Inset inside the bubble, so its corners run parallel to the bubble's.
    val inner = BrShapes.concentric(Round, BubblePadding)
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
                .clip(inner)
                .background(colors.surfaceHigh),
        )
    } else {
        Row(
            Modifier
                .clip(inner)
                .background(colors.surfaceHigh)
                .padding(horizontal = BrSpacing.M, vertical = BrSpacing.S),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_attach_file), contentDescription = null, tint = colors.textSecondary)
            }
            Text(
                attachment.name ?: attachment.mimeType,
                style = BedrockTheme.typography.labelSmall,
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
