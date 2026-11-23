package dev.redfox.messages.feature.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.redfox.bedrock.components.BrIconButton
import dev.redfox.bedrock.components.BrSearchField
import dev.redfox.bedrock.components.IconButtonVariant
import dev.redfox.bedrock.components.SectionHeader
import dev.redfox.bedrock.effects.glass
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.motion.BrDurations
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.Avatar
import dev.redfox.messages.core.ui.components.ConversationRow
import dev.redfox.messages.core.ui.format.TimeFormat
import kotlinx.coroutines.delay

/** BrSearchField's height. */
private val FieldHeight = 48.dp
/** Matches the nav bar's gap above the gesture area, so the field sits where the bar was. */
private val BottomGap = 12.dp
/** How far the field and results rise as search opens. */
private val Rise = 16.dp

/**
 * Search, without leaving the screen: a frosted layer fades in over the current tab while the
 * search field rises a little into place just above the keyboard, and results stack up above it
 * (nearest the thumb first). Back, or a tap outside the results, fades it away again; the chevron
 * beside the field only puts the keyboard away.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchOverlay(
    open: Boolean,
    onClose: () -> Unit,
    onOpenThread: (threadId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val messages = viewModel.messages.collectAsLazyPagingItems()
    val progress = remember { Animatable(0f) }
    val latestClose by rememberUpdatedState(onClose)
    val focus = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LaunchedEffect(open) {
        if (open) {
            progress.animateTo(1f, tween(BrDurations.Enter, easing = BrMotion.Calm))
        } else {
            focusManager.clearFocus()
            keyboard?.hide()
            progress.animateTo(0f, tween(BrDurations.Swap, easing = BrMotion.Calm))
            viewModel.dispatch(SearchIntent.Closed)
        }
    }
    LaunchedEffect(open) {
        if (open) {
            delay(60) // Let the fade start before the keyboard pushes everything up.
            // Retry briefly: the field may still be attaching on the first frames.
            repeat(5) {
                if (runCatching { focus.requestFocus() }.isSuccess) {
                    keyboard?.show()
                    return@LaunchedEffect
                }
                delay(40)
            }
        }
    }
    BackHandler(enabled = open) { latestClose() }

    val shown by remember { derivedStateOf { progress.value > 0f } }
    if (!shown && !open) return

    val colors = BedrockTheme.colors
    Box(modifier.fillMaxSize()) {
        // Frosts the page underneath, fading in as search opens; tapping it closes search.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = progress.value }
                .glass(RectangleShape, tint = colors.glassStrong, rim = false)
                .pointerInput(Unit) { detectTapGestures { latestClose() } }
        )

        val insets = WindowInsets.ime.union(WindowInsets.navigationBars)
        Results(
            state = state,
            messages = messages,
            onOpenThread = { threadId ->
                onOpenThread(threadId)
                latestClose()
            },
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(insets)
                .padding(bottom = BottomGap + FieldHeight + BrSpacing.M)
                .graphicsLayer {
                    // A beat behind the field, so the field leads.
                    val p = ((progress.value - 0.2f) / 0.8f).coerceIn(0f, 1f)
                    alpha = p
                    translationY = (1f - p) * Rise.toPx()
                },
        )

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(insets)
                .padding(horizontal = BrSpacing.Gutter)
                .padding(bottom = BottomGap)
                .graphicsLayer {
                    val p = progress.value
                    alpha = p
                    translationY = (1f - p) * Rise.toPx()
                },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
        ) {
            // The field is there from the first frame (just transparent), so it can take focus,
            // and bring up the keyboard, the moment search opens.
            BrSearchField(
                query = state.query,
                onQueryChange = { query ->
                    viewModel.dispatch(if (query.isEmpty()) SearchIntent.Clear else SearchIntent.QueryChanged(query))
                },
                placeholder = "Search messages and people",
                // The keyboard shows a search key; pressing it searches right away.
                onSearch = {
                    viewModel.dispatch(SearchIntent.Submit)
                    keyboard?.hide()
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            // Only while the keyboard is up: there's nothing to put away otherwise.
            AnimatedVisibility(
                visible = WindowInsets.isImeVisible,
                enter = BrTransitions.revealStart,
                exit = BrTransitions.concealStart,
            ) {
                BrIconButton(
                    painterResource(R.drawable.ic_keyboard_arrow_down),
                    contentDescription = "Hide keyboard",
                    onClick = {
                        focusManager.clearFocus()
                        keyboard?.hide()
                    },
                    variant = IconButtonVariant.Glass,
                    haptic = HapticFeedbackType.ContextClick,
                )
            }
        }
    }
}

/**
 * Results stacked upward from the field (reverse layout: the first item sits right above it).
 * People first, nearest the thumb, then matching messages, newest nearest.
 */
@Composable
private fun Results(
    state: SearchState,
    messages: LazyPagingItems<SearchRow>,
    onOpenThread: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeFormat = remember { TimeFormat() }
    val results = state.results
    val now = remember(results) { System.currentTimeMillis() }
    // The count comes from the index; while it's still being built, from what has loaded.
    val messageCount = results?.messageCount
        ?: if (messages.loadState.refresh is LoadState.NotLoading) messages.itemCount else null
    val nothing = results != null && results.conversations.isEmpty() && messageCount == 0

    LazyColumn(
        modifier = modifier,
        reverseLayout = true,
        contentPadding = PaddingValues(vertical = BrSpacing.S),
    ) {
        when {
            state.query.isBlank() -> item(key = "hint") {
                Notice("Search your messages", "Find words in any message, or a person by name or number.")
            }
            state.tooShort -> Unit
            results == null || results.query != state.query.trim() && nothing ->
                item(key = "searching") { Notice("Searching…", null) }
            nothing -> item(key = "none") {
                Notice("No results", "Nothing matches “${state.query.trim()}”.")
            }
            else -> {
                if (results.conversations.isNotEmpty()) {
                    items(results.conversations, key = { "c" + it.threadId }) { conversation ->
                        ConversationRow(
                            conversation = conversation,
                            timeLabel = timeFormat.conversationTime(conversation.timestamp, now),
                            onClick = { onOpenThread(conversation.threadId) },
                        )
                    }
                    item(key = "people") { SectionTitle("People") }
                }
                if (messages.itemCount > 0) {
                    // Only rows near the screen are loaded; the rest page in while scrolling.
                    items(
                        count = messages.itemCount,
                        key = messages.itemKey { "m" + it.hit.message.key },
                        contentType = messages.itemContentType { "hit" },
                    ) { index ->
                        val row = messages[index] ?: return@items
                        HitRow(row) { onOpenThread(row.hit.message.threadId) }
                    }
                    item(key = "messages") {
                        SectionTitle(if (messageCount != null) "Messages · $messageCount" else "Messages")
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    SectionHeader(text, Modifier.padding(horizontal = BrSpacing.Gutter).padding(top = BrSpacing.S))
}

@Composable
private fun Notice(title: String, body: String?) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.XL),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(BrSpacing.XS),
    ) {
        Text(title, style = BedrockTheme.typography.headline, color = BedrockTheme.colors.textPrimary, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, style = BedrockTheme.typography.body, color = BedrockTheme.colors.textSecondary, textAlign = TextAlign.Center)
        }
    }
}

/** One matching message: who, when, and the text around the match with the matches highlighted. */
@Composable
private fun HitRow(row: SearchRow, onClick: () -> Unit) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val hit = row.hit
    val message = hit.message
    val people = hit.conversation?.participants ?: listOfNotNull(message.sender)
    val title = hit.conversation?.title ?: message.sender?.displayName ?: "Unknown"
    val accent = colors.accentSoft
    val snippet = remember(row.excerpt, accent) { row.excerpt.annotated(accent) }
    Row(
        Modifier
            .fillMaxWidth()
            .pressable(pressedScale = 0.99f, onClick = onClick)
            .padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.S),
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.M),
        verticalAlignment = Alignment.Top,
    ) {
        Avatar(people, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (message.isOutgoing) "You → " else "") + title,
                    style = type.label,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(row.timeLabel, style = type.caption.copy(fontFeatureSettings = "tnum"), color = colors.textTertiary)
            }
            Text(snippet, style = type.body, color = colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
