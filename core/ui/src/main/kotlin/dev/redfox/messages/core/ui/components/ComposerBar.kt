package dev.redfox.messages.core.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.redfox.bedrock.components.BrIconButton
import dev.redfox.bedrock.components.IconButtonVariant
import dev.redfox.bedrock.effects.floatShadow
import dev.redfox.bedrock.effects.glass
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.motion.BrDurations
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.motion.RollingNumber
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.model.OutgoingAttachment
import dev.redfox.messages.core.model.SimCard
import dev.redfox.messages.core.ui.R

private val ComposerRadius = 26.dp
private val ComposerShape = RoundedCornerShape(ComposerRadius)
private val SendSize = 44.dp

/** Thumbnails sit inset by the bar's padding plus the strip's, so their corners run parallel. */
private val ThumbShape = BrShapes.concentric(ComposerRadius, BrSpacing.S)

/**
 * The message composer (PLAN.md §7.3): a floating frosted bar like the nav bar, with the SIM
 * badge (dual-SIM phones only), the text field and the send button. Send is a solid accent
 * circle only while there's something to send; otherwise it rests on a neutral fill. A segment
 * counter eases open above the field once a message gets long.
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
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = BrSpacing.Gutter - BrSpacing.XXS)
            // It floats over the thread: the one neutral shadow, then frosted glass with a lit rim.
            .floatShadow(ComposerShape)
            .glass(ComposerShape)
            .padding(BrSpacing.XS),
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
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    Row(
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.XS),
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
                .padding(start = if (showSim || onAttachClick != null) 0.dp else BrSpacing.M),
            verticalArrangement = Arrangement.Center,
        ) {
            AnimatedVisibility(visible = counter != null, enter = BrTransitions.expandIn, exit = BrTransitions.collapseOut) {
                Text(
                    counter.orEmpty(),
                    style = type.figure.copy(fontSize = type.caption.fontSize, lineHeight = type.caption.lineHeight),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = BrSpacing.XXS),
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
                    cursorBrush = SolidColor(colors.accentMain),
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

/** While recording: a slowly breathing dot and the time, cancel, and stop (which adds the note to the message). */
@Composable
private fun RecordingRow(elapsedMillis: Long, onStop: () -> Unit, onCancel: () -> Unit) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val pulse = rememberInfiniteTransition(label = "rec").animateFloat(
        initialValue = 1f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(BrDurations.Linger * 2, easing = BrMotion.Soft), RepeatMode.Reverse),
        label = "recDot",
    )
    Row(
        Modifier.fillMaxWidth().heightIn(min = SendSize),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.M),
    ) {
        RoundIconButton(R.drawable.ic_close, "Cancel recording", onClick = onCancel)
        Box(
            Modifier
                .size(10.dp)
                .graphicsLayer { alpha = pulse.value }
                .clip(CircleShape)
                .background(colors.critical),
        )
        RollingNumber(
            formatDuration(elapsedMillis),
            style = type.figure.copy(fontSize = type.bodyLarge.fontSize, lineHeight = type.bodyLarge.lineHeight),
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        RoundIconButton(R.drawable.ic_stop, "Stop recording", onClick = onStop, filled = true)
    }
}

/** Thumbnails and voice-note chips above the field, each removable. */
@Composable
private fun AttachmentStrip(attachments: List<OutgoingAttachment>, onRemove: (OutgoingAttachment) -> Unit) {
    val colors = BedrockTheme.colors
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(BrSpacing.XS),
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
    ) {
        attachments.forEach { attachment ->
            Box {
                if (attachment.isImage) {
                    AsyncImage(
                        model = attachment.uri,
                        contentDescription = "Photo to send",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(72.dp).clip(ThumbShape).background(colors.surfaceHigh),
                    )
                } else {
                    Box(
                        Modifier.clip(ThumbShape).background(colors.surfaceHigh).padding(horizontal = BrSpacing.S),
                    ) { AudioPlayerView(attachment.uri) }
                }
                // A 32dp target around a small scrimmed circle, so it's easy to hit without covering the photo.
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .size(32.dp)
                        .pressable(pressedScale = 0.94f) { onRemove(attachment) },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            // Photo scrim: dark enough to read over any picture.
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(painterResource(R.drawable.ic_close), contentDescription = "Remove", tint = colors.textPrimary, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(icon: Int, description: String, onClick: () -> Unit, filled: Boolean = false) {
    BrIconButton(
        painterResource(icon),
        contentDescription = description,
        onClick = onClick,
        variant = if (filled) IconButtonVariant.Primary else IconButtonVariant.Plain,
        size = SendSize,
        tint = if (filled) null else BedrockTheme.colors.textSecondary,
        haptic = HapticFeedbackType.ContextClick,
    )
}

@Composable
private fun SendButton(enabled: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?) {
    val colors = BedrockTheme.colors
    val fill by animateColorAsState(if (enabled) colors.accentMain else colors.surfaceHigh, BrMotion.quick(), label = "sendFill")
    val tint by animateColorAsState(if (enabled) colors.onAccent else colors.textDisabled, BrMotion.quick(), label = "sendTint")
    Box(
        Modifier
            .size(SendSize)
            .pressable(
                enabled = enabled,
                pressedScale = 0.94f,
                haptic = HapticFeedbackType.Confirm,
                onLongClick = onLongClick,
                onClick = onClick,
            )
            .clip(CircleShape)
            .background(fill),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(R.drawable.ic_send), contentDescription = "Send. Long-press to schedule.", tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** "SIM 1" / "SIM 2" pill; tapping switches SIM. */
@Composable
private fun SimBadge(sim: SimCard, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = BedrockTheme.colors
    Row(
        modifier
            .heightIn(min = 32.dp)
            .pressable(pressedScale = 0.94f, haptic = HapticFeedbackType.SegmentTick, onClick = onClick)
            .clip(CircleShape)
            .background(colors.surfaceHigh)
            .padding(horizontal = BrSpacing.S, vertical = BrSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.XXS),
    ) {
        Icon(
            painterResource(R.drawable.ic_sim_card),
            contentDescription = "Sending from ${sim.label}. Tap to switch SIM.",
            tint = colors.accentSoft,
            modifier = Modifier.size(16.dp),
        )
        Text(
            "${sim.slotIndex + 1}",
            style = BedrockTheme.typography.figure.copy(fontWeight = FontWeight.Medium),
            color = colors.textPrimary,
        )
    }
}

/** A short notice in place of the composer when replying isn't possible here (e.g. group MMS). */
@Composable
fun ComposerNotice(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .glass(CircleShape)
            .padding(horizontal = BrSpacing.L, vertical = BrSpacing.S),
    ) {
        Text(text, style = BedrockTheme.typography.labelSmall, color = BedrockTheme.colors.textSecondary)
    }
}
