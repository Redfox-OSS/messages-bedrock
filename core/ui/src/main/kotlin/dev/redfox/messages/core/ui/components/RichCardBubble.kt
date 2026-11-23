package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.redfox.messages.core.model.MediaHeight
import dev.redfox.messages.core.model.RichCard
import dev.redfox.messages.core.model.RichContent
import dev.redfox.messages.core.model.RichMedia
import dev.redfox.messages.core.model.RichSuggestion
import dev.redfox.messages.core.model.SuggestionAction
import dev.redfox.messages.core.ui.R
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing

private val CardShape = BrShapes.Large
private val SingleCardWidth = 300.dp
private val CarouselCardWidth = 248.dp

/**
 * An RCS business chatbot card (or carousel of cards) in the thread: image, title, description
 * and its link / call buttons. Canned-reply buttons are omitted — answering a chatbot needs RCS,
 * which this app deliberately doesn't speak.
 *
 * Images are remote and always load; the placeholder tile shows until they arrive (or if they fail).
 */
@Composable
fun RichCardBubble(
    rich: RichContent,
    timeLabel: String?,
    onSuggestion: (RichSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        if (rich.isCarousel) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = BrSpacing.Gutter),
                horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
            ) {
                itemsIndexed(rich.cards) { _, card ->
                    CardView(card, CarouselCardWidth, onSuggestion)
                }
            }
        } else {
            CardView(rich.cards.first(), SingleCardWidth, onSuggestion, Modifier.padding(horizontal = BrSpacing.Gutter))
        }
        if (timeLabel != null) {
            Text(
                timeLabel,
                style = BedrockTheme.typography.figure.copy(
                    fontSize = BedrockTheme.typography.caption.fontSize,
                    lineHeight = BedrockTheme.typography.caption.lineHeight,
                ),
                color = BedrockTheme.colors.textTertiary,
                modifier = Modifier.padding(top = BrSpacing.XXS, start = BrSpacing.Gutter + BrSpacing.S),
            )
        }
    }
}

@Composable
private fun CardView(
    card: RichCard,
    width: Dp,
    onSuggestion: (RichSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val actions = card.suggestions.filter { it.action !is SuggestionAction.Reply }

    Column(
        modifier
            .width(width)
            .clip(CardShape)
            .background(colors.surfaceRaised),
    ) {
        card.media?.let { MediaView(it) }
        Column(
            Modifier.padding(horizontal = 14.dp, vertical = BrSpacing.M),
            verticalArrangement = Arrangement.spacedBy(BrSpacing.XS),
        ) {
            card.title?.let {
                Text(it, style = type.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = colors.textPrimary)
            }
            card.description?.let {
                LinkifiedText(it, style = type.body, color = colors.textSecondary)
            }
        }
        if (actions.isNotEmpty()) {
            Column(
                Modifier.padding(start = BrSpacing.M, end = BrSpacing.M, bottom = BrSpacing.M),
                verticalArrangement = Arrangement.spacedBy(BrSpacing.XS),
            ) {
                actions.forEach { SuggestionButton(it, onSuggestion) }
            }
        }
    }
}

@Composable
private fun MediaView(media: RichMedia) {
    val colors = BedrockTheme.colors
    // Prefer the sender's real aspect ratio; fall back to its height hint.
    val sized = media.aspectRatio?.let { Modifier.aspectRatio(it.coerceIn(0.6f, 2.4f)) }
        ?: Modifier.height(
            when (media.height) {
                MediaHeight.Short -> 112.dp
                MediaHeight.Medium -> 168.dp
                MediaHeight.Tall -> 264.dp
            }
        )
    Box(
        Modifier.fillMaxWidth().then(sized).background(colors.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        // The image or video lives on the business's server, and the app has no internet access
        // (nothing leaves the device, and nothing is fetched): the tile marks where it would be.
        if (media.isVideo) {
            Icon(painterResource(R.drawable.ic_play_circle), null, tint = colors.textTertiary, modifier = Modifier.size(44.dp))
        } else {
            Icon(painterResource(R.drawable.ic_image), null, tint = colors.textTertiary)
        }
    }
}

@Composable
private fun SuggestionButton(suggestion: RichSuggestion, onSuggestion: (RichSuggestion) -> Unit) {
    val colors = BedrockTheme.colors
    val icon = when (suggestion.action) {
        is SuggestionAction.Dial -> R.drawable.ic_call
        else -> R.drawable.ic_open_in_new
    }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.97f) { onSuggestion(suggestion) }
            .clip(CircleShape)
            .background(colors.accentContainer)
            .padding(horizontal = BrSpacing.L, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(painterResource(icon), null, tint = colors.accentSoft, modifier = Modifier.size(16.dp))
        Text(
            suggestion.label,
            style = BedrockTheme.typography.label,
            color = colors.accentSoft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = BrSpacing.S),
        )
    }
}
