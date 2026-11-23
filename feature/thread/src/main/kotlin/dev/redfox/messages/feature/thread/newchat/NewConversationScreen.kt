package dev.redfox.messages.feature.thread.newchat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.redfox.bedrock.components.BrIconButton
import dev.redfox.bedrock.components.BrLargeTitle
import dev.redfox.bedrock.components.BrTopBar
import dev.redfox.bedrock.components.EmptyState
import dev.redfox.bedrock.components.IconButtonVariant
import dev.redfox.bedrock.components.IconTile
import dev.redfox.bedrock.components.ListRow
import dev.redfox.bedrock.components.rememberTopBarCollapse
import dev.redfox.bedrock.components.topBarClearance
import dev.redfox.bedrock.effects.glassSource
import dev.redfox.bedrock.motion.revealOnEnter
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.Avatar
import dev.redfox.messages.core.ui.mvi.CollectEffects
import kotlinx.serialization.Serializable

/** The recipient picker; [draft] is text shared from another app, carried into the chosen thread. */
@Serializable
data class NewConversationKey(val draft: String? = null) : NavKey

@Composable
fun NewConversationRoute(
    onBack: () -> Unit,
    onOpenThread: (threadId: Long, address: String, title: String) -> Unit,
    viewModel: NewConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is NewConversationEffect.OpenThread -> onOpenThread(effect.threadId, effect.address, effect.title)
        }
    }
    NewConversationScreen(state = state, onBack = onBack, onIntent = viewModel::dispatch)
}

@Composable
fun NewConversationScreen(
    state: NewConversationState,
    onBack: () -> Unit,
    onIntent: (NewConversationIntent) -> Unit,
) {
    val listState = rememberLazyListState()
    val collapse = rememberTopBarCollapse(listState)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BrSpacing.XXL
    Box(Modifier.fillMaxSize().imePadding()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().glassSource(),
            contentPadding = PaddingValues(top = topBarClearance(), bottom = bottom),
        ) {
            item(key = "title") { BrLargeTitle(title = "New conversation", collapse = collapse) }
            item(key = "field") {
                RecipientField(
                    query = state.query,
                    onQueryChange = { onIntent(NewConversationIntent.QueryChanged(it)) },
                    onDone = { state.typedNumber?.let { onIntent(NewConversationIntent.Pick(it, it)) } },
                )
            }
            state.typedNumber?.let { number ->
                item(key = "typed") {
                    ListRow(
                        title = "Send to $number",
                        leading = {
                            IconTile(
                                painterResource(R.drawable.ic_dialpad),
                                tint = BedrockTheme.colors.accentSoft,
                                container = BedrockTheme.colors.accentContainer,
                            )
                        },
                        onClick = { onIntent(NewConversationIntent.Pick(number, number)) },
                        modifier = Modifier.padding(horizontal = BrSpacing.Gutter - BrSpacing.L),
                    )
                }
            }
            items(state.results, key = { "c:" + it.address + it.name }) { contact ->
                ContactRow(
                    contact = contact,
                    onClick = { onIntent(NewConversationIntent.Pick(contact.address, contact.displayName)) },
                    modifier = Modifier.revealOnEnter(minOf(state.results.indexOf(contact), 10)),
                )
            }
            if (state.results.isEmpty() && state.typedNumber == null) {
                item(key = "empty") {
                    EmptyState(
                        icon = painterResource(R.drawable.ic_contacts),
                        title = if (state.query.isBlank()) "No contacts" else "No matches",
                        message = "Type a phone number to message it directly",
                    )
                }
            }
        }
        BrTopBar(
            title = "New conversation",
            collapse = collapse,
            modifier = Modifier.align(Alignment.TopCenter),
            onBack = onBack,
        )
    }
}

@Composable
private fun RecipientField(query: String, onQueryChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.S)
            .background(colors.surfaceRaised, BrShapes.Pill)
            .border(1.dp, colors.line, BrShapes.Pill)
            .heightIn(min = 52.dp)
            .padding(start = BrSpacing.L, end = BrSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.M),
    ) {
        Text("To", style = type.label, color = colors.textSecondary)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Name or number", style = type.body, color = colors.textTertiary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accentMain),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            BrIconButton(
                painterResource(R.drawable.ic_close),
                contentDescription = "Clear",
                onClick = { onQueryChange("") },
                variant = IconButtonVariant.Plain,
                size = 36.dp,
                tint = colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit, modifier: Modifier = Modifier) {
    ListRow(
        title = contact.displayName,
        subtitle = if (contact.name != null) contact.formattedAddress else null,
        leading = { Avatar(listOf(contact), size = 40.dp) },
        onClick = onClick,
        modifier = modifier.padding(horizontal = BrSpacing.Gutter - BrSpacing.L),
    )
}
