package dev.redfox.messages.core.ui.theme

import androidx.compose.runtime.Composable
import dev.redfox.bedrock.theme.BedrockAccent
import dev.redfox.bedrock.theme.BedrockNeutrals
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.messages.core.model.AppAccent

/**
 * Thin adapter from the persisted domain [AppAccent] onto [BedrockTheme]. Bedrock is always dark;
 * the neutral ramp is fixed to [BedrockNeutrals.Basalt]. A new accent needs an entry in both.
 */
@Composable
fun MessagesTheme(
    accent: AppAccent = AppAccent.Default,
    content: @Composable () -> Unit,
) {
    BedrockTheme(accent = accent.toBedrock(), neutrals = BedrockNeutrals.Basalt, content = content)
}

fun AppAccent.toBedrock(): BedrockAccent = when (this) {
    AppAccent.Tide -> BedrockAccent.Tide
    AppAccent.Iris -> BedrockAccent.Iris
    AppAccent.Moss -> BedrockAccent.Moss
    AppAccent.Rose -> BedrockAccent.Rose
    AppAccent.Ember -> BedrockAccent.Ember
    AppAccent.Sand -> BedrockAccent.Sand
}
