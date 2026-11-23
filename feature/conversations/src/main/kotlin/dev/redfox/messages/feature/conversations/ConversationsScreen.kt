package dev.redfox.messages.feature.conversations

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import dev.redfox.bedrock.motion.BrDurations
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.heightIn
import dev.redfox.bedrock.components.TopBarHeight
import dev.redfox.bedrock.motion.SwapText
import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.redfox.bedrock.components.BrButton
import dev.redfox.bedrock.components.BrChip
import dev.redfox.bedrock.components.BrDialog
import dev.redfox.bedrock.components.BrIconButton
import dev.redfox.bedrock.components.BrLargeTitle
import dev.redfox.bedrock.components.BrProgressBar
import dev.redfox.bedrock.components.BrTopBar
import dev.redfox.bedrock.components.EmptyState
import dev.redfox.bedrock.components.IconButtonVariant
import dev.redfox.bedrock.components.rememberTopBarCollapse
import dev.redfox.bedrock.components.topBarClearance
import dev.redfox.bedrock.effects.glassSource
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.motion.revealOnEnter
import dev.redfox.bedrock.navigation.brNavClearance
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.domain.usecase.InboxFilter
import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.ConversationRow
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.messages.core.ui.platform.rememberCaller
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlin.math.abs
import kotlin.math.roundToInt

@Serializable
data object ConversationsKey : NavKey

private val FilterLabels = mapOf(
    InboxFilter.All to "All",
    InboxFilter.Unread to "Unread",
    InboxFilter.Personal to "Personal",
    InboxFilter.Transactions to "Transactions",
    InboxFilter.Otp to "OTPs",
    InboxFilter.Offers to "Offers",
    InboxFilter.Archived to "Archived",
)

/** Rows beyond the first screenful skip the staggered entrance. */
private const val RevealedRows = 12

/** Reading the shared SMS store; contacts turn numbers into names and photos. */
private val AccessPermissions = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)

@Composable
fun ConversationsRoute(
    onOpenThread: (threadId: Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: ConversationsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val conversations = viewModel.conversations.collectAsLazyPagingItems()
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.dispatch(ConversationsIntent.Refresh)
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(ConversationsIntent.Refresh) }
    val call = rememberCaller()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ConversationsEffect.Call -> call(effect.phoneNumber)
            is ConversationsEffect.OpenThread -> onOpenThread(effect.threadId)
            ConversationsEffect.RequestSmsAccess -> permissions.launch(AccessPermissions)
        }
    }
    ConversationsScreen(
        state = state,
        conversations = conversations,
        onOpenSettings = onOpenSettings,
        onIntent = viewModel::dispatch,
    )
}

@Composable
fun ConversationsScreen(
    state: ConversationsState,
    conversations: LazyPagingItems<ConversationItem>,
    onOpenSettings: () -> Unit,
    onIntent: (ConversationsIntent) -> Unit,
) {
    val colors = BedrockTheme.colors
    val settled = conversations.loadState.refresh is LoadState.NotLoading && !state.isIndexing
    BackHandler(enabled = state.isSelecting) { onIntent(ConversationsIntent.ClearSelection) }
    val title = if (state.isSelecting) "${state.selected.size} selected" else "Messages"
    val listState = rememberLazyListState()
    val listCollapse = rememberTopBarCollapse(listState)
    // The access prompt doesn't scroll: its title stays large and the bar stays clear.
    val restingCollapse = remember { mutableFloatStateOf(0f) }
    val collapse: State<Float> = if (state.hasSmsAccess) listCollapse else restingCollapse

    Box(Modifier.fillMaxSize()) {
        if (!state.hasSmsAccess) {
            AccessPrompt(title, collapse, onIntent)
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().glassSource(),
                contentPadding = PaddingValues(top = statusBarTop(), bottom = brNavClearance()),
            ) {
                item(key = "title", contentType = "title") {
                    InboxHeader(
                        title = title,
                        collapse = collapse,
                        actions = { InboxActions(state, onIntent, onOpenSettings) },
                    )
                }
                item(key = "filters", contentType = "filters") {
                    FilterRow(selected = state.filter, onIntent = onIntent)
                }
                // In the list, under the filters: it can't overlap them, and since it only shows
                // while the inbox is still empty (the first sync), nothing below it moves.
                item(key = "sync", contentType = "sync") {
                    SyncProgress(
                        visible = state.isIndexing && conversations.itemCount == 0,
                        progress = state.syncProgress,
                        label = "Syncing messages",
                        modifier = Modifier.padding(top = BrSpacing.S),
                    )
                }
                if (settled && conversations.itemCount == 0) {
                    item(key = "empty", contentType = "empty") {
                        Box(Modifier.fillMaxWidth().height(420.dp)) {
                            EmptyState(
                                icon = painterResource(R.drawable.ic_chat),
                                title = if (state.filter == InboxFilter.All) "No conversations yet" else "Nothing here",
                                message = if (state.filter == InboxFilter.All) "Messages you send and receive appear here."
                                else "No ${FilterLabels.getValue(state.filter)} conversations.",
                            )
                        }
                    }
                }
                items(
                    count = conversations.itemCount,
                    key = conversations.itemKey { it.conversation.threadId },
                    contentType = conversations.itemContentType { "conversation" },
                ) { index ->
                    val item = conversations[index] ?: return@items
                    val conversation = item.conversation
                    SwipeActionRow(
                        // No swipes while selecting: a drag there is almost always a stray one.
                        left = if (state.isSelecting) null else swipeVisual(state.swipeLeft, conversation),
                        right = if (state.isSelecting) null else swipeVisual(state.swipeRight, conversation),
                        onSwipeLeft = { onIntent(ConversationsIntent.Swiped(conversation, left = true)) },
                        onSwipeRight = { onIntent(ConversationsIntent.Swiped(conversation, left = false)) },
                        modifier = Modifier
                            .animateItem(placementSpec = BrMotion.settle(IntOffset.VisibilityThreshold))
                            // Only the first screenful plays the entrance; paged-in rows just appear.
                            .then(if (index < RevealedRows) Modifier.revealOnEnter(index + 1) else Modifier),
                    ) {
                        ConversationRow(
                            conversation = conversation,
                            timeLabel = item.timeLabel,
                            onClick = { onIntent(ConversationsIntent.ConversationClicked(conversation)) },
                            onLongClick = { onIntent(ConversationsIntent.ConversationLongPressed(conversation)) },
                            selected = conversation.threadId in state.selected,
                            // Opaque, so the swipe's track only shows where the row has moved away.
                            modifier = Modifier.background(colors.background),
                        )
                    }
                }
            }
        }

        BrTopBar(
            title = title,
            collapse = collapse,
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            // The header row carries the actions while it's on screen; the bar takes them over
            // once that row has slid under it, so there's only ever one set to tap.
            val showActions by remember { derivedStateOf { collapse.value > 0.5f } }
            AnimatedVisibility(
                visible = showActions,
                enter = fadeIn(tween(BrDurations.Swap, easing = BrMotion.Calm)),
                exit = fadeOut(tween(BrDurations.Fade)),
            ) {
                InboxActions(state, onIntent, onOpenSettings)
            }
        }
    }

    // Always composed so it can ease out; it keeps showing what it asked about while it leaves.
    var lastConfirm by remember { mutableStateOf(state.confirmDelete) }
    if (state.confirmDelete.isNotEmpty()) lastConfirm = state.confirmDelete
    val single = lastConfirm.singleOrNull()
    BrDialog(
        visible = state.confirmDelete.isNotEmpty(),
        onDismiss = { onIntent(ConversationsIntent.CancelDelete) },
        title = if (single != null) "Delete conversation?" else "Delete ${lastConfirm.size} conversations?",
        message = if (single != null) "All messages with ${single.title} are removed from this phone for good."
        else "All their messages are removed from this phone for good.",
        confirmLabel = "Delete",
        onConfirm = { onIntent(ConversationsIntent.ConfirmDelete) },
        destructive = true,
    )
}

/** The status bar's height: the inbox header sits right under it, in the top bar's place. */
@Composable
private fun statusBarTop(): Dp = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

/**
 * The inbox's large title with its actions on the same row, where a page's top bar would be. It
 * fades as the list scrolls under the frosted bar, which then shows the small title and the
 * actions. While selecting, the count replaces the title in a smaller size so the four selection
 * actions fit beside it.
 */
@Composable
private fun InboxHeader(title: String, collapse: State<Float>, actions: @Composable () -> Unit) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val selecting = title != "Messages"
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = TopBarHeight)
            .padding(start = BrSpacing.Gutter, end = 12.dp, bottom = BrSpacing.L)
            .graphicsLayer { alpha = 1f - collapse.value },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SwapText(
            text = title,
            style = if (selecting) type.headline else type.title,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f).revealOnEnter(0),
        )
        actions()
    }
}

/** Settings, or the bulk actions while selecting; in the header row and later the top bar. */
@Composable
private fun InboxActions(state: ConversationsState, onIntent: (ConversationsIntent) -> Unit, onOpenSettings: () -> Unit) {
    AnimatedContent(
        targetState = state.isSelecting,
        transitionSpec = { BrTransitions.swap },
        label = "inboxActions",
    ) { selecting ->
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                SelectionActions(state, onIntent)
            } else {
                BrIconButton(
                    painterResource(R.drawable.ic_settings),
                    contentDescription = "Settings",
                    onClick = onOpenSettings,
                    variant = IconButtonVariant.Glass,
                )
            }
        }
    }
}

/** The top bar's bulk actions while selecting. Labels follow the selection, like the swipes do. */
@Composable
private fun SelectionActions(state: ConversationsState, onIntent: (ConversationsIntent) -> Unit) {
    BrIconButton(
        painterResource(if (state.selectionMarksRead) R.drawable.ic_mark_chat_read else R.drawable.ic_mark_chat_unread),
        contentDescription = if (state.selectionMarksRead) "Mark read" else "Mark unread",
        onClick = { onIntent(ConversationsIntent.ToggleReadSelected) },
        variant = IconButtonVariant.Glass,
    )
    BrIconButton(
        painterResource(if (state.selectionUnarchives) R.drawable.ic_unarchive else R.drawable.ic_archive),
        contentDescription = if (state.selectionUnarchives) "Unarchive" else "Archive",
        onClick = { onIntent(ConversationsIntent.ArchiveSelected) },
        variant = IconButtonVariant.Glass,
    )
    BrIconButton(
        painterResource(R.drawable.ic_delete),
        contentDescription = "Delete",
        onClick = { onIntent(ConversationsIntent.DeleteSelected) },
        variant = IconButtonVariant.Glass,
    )
    BrIconButton(
        painterResource(R.drawable.ic_close),
        contentDescription = "Cancel selection",
        onClick = { onIntent(ConversationsIntent.ClearSelection) },
        variant = IconButtonVariant.Glass,
    )
}

/** How one swipe direction looks: what's revealed behind the row. */
private data class SwipeVisual(val label: String, @param:DrawableRes val icon: Int, val destructive: Boolean = false)

/** What a swipe reveals for this conversation; null when the action doesn't apply (e.g. calling a bank). */
private fun swipeVisual(action: SwipeAction, conversation: Conversation): SwipeVisual? = when (action) {
    SwipeAction.None -> null
    SwipeAction.Archive ->
        if (conversation.isArchived) SwipeVisual("Unarchive", R.drawable.ic_unarchive)
        else SwipeVisual("Archive", R.drawable.ic_archive)
    SwipeAction.Delete -> SwipeVisual("Delete", R.drawable.ic_delete, destructive = true)
    SwipeAction.ToggleRead ->
        if (conversation.isUnread) SwipeVisual("Mark read", R.drawable.ic_mark_chat_read)
        else SwipeVisual("Mark unread", R.drawable.ic_mark_chat_unread)
    SwipeAction.Pin -> SwipeVisual(if (conversation.isPinned) "Unpin" else "Pin", R.drawable.ic_keep)
    SwipeAction.Call -> callableNumber(conversation)?.let { SwipeVisual("Call", R.drawable.ic_call) }
}

/** How far (as a share of the row's width) a swipe must travel before letting go fires it. */
private const val SwipeThreshold = 0.35f

/**
 * Gmail-style swipe on a list row. The row follows the finger over a quiet track; past the
 * threshold the action's circle firms up, and letting go fires the action (with a haptic) while
 * the row settles back on a critically damped spring. The list itself then removes or updates the
 * row (archive, delete…). A null visual disables that direction.
 */
@Composable
private fun SwipeActionRow(
    /** Swiping from right to left. */
    left: SwipeVisual?,
    /** Swiping from left to right. */
    right: SwipeVisual?,
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val latestLeft by rememberUpdatedState(onSwipeLeft)
    val latestRight by rememberUpdatedState(onSwipeRight)
    val canLeft by rememberUpdatedState(left != null)
    val canRight by rememberUpdatedState(right != null)
    val scope = rememberCoroutineScope()
    val offset = remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(0) }
    var settle by remember { mutableStateOf<Job?>(null) }
    val flingVelocity = with(LocalDensity.current) { 1200.dp.toPx() }

    // Which side is showing and whether letting go would fire: these change rarely, so the track
    // recomposes only when they flip, never per frame.
    val towardsStart by remember { derivedStateOf { offset.floatValue < 0f } }
    val armed by remember {
        derivedStateOf { width > 0 && abs(offset.floatValue) >= width * SwipeThreshold }
    }

    val dragState = rememberDraggableState { delta ->
        val min = if (canLeft) -width.toFloat() else 0f
        val max = if (canRight) width.toFloat() else 0f
        offset.floatValue = (offset.floatValue + delta).coerceIn(min, max)
    }

    Box(
        modifier
            .onSizeChanged { width = it.width }
            .draggable(
                state = dragState,
                orientation = Orientation.Horizontal,
                enabled = left != null || right != null,
                onDragStarted = { settle?.cancel() },
                onDragStopped = { velocity ->
                    val x = offset.floatValue
                    val far = width > 0 && abs(x) >= width * SwipeThreshold
                    // A quick flick in the same direction counts too, once it's well under way.
                    val flung = width > 0 && abs(velocity) > flingVelocity && velocity * x > 0f &&
                        abs(x) >= width * SwipeThreshold / 2
                    if (x != 0f && (far || flung)) {
                        if (x < 0f) latestLeft() else latestRight()
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                    // Back to rest either way; the action's own result (row gone, badge changed) shows it worked.
                    settle = scope.launch {
                        animate(offset.floatValue, 0f, initialVelocity = velocity, animationSpec = BrMotion.settle()) { value, _ ->
                            offset.floatValue = value
                        }
                    }
                },
            ),
    ) {
        val visual = if (towardsStart) left else right
        if (visual != null) {
            SwipeTrack(visual, towardsStart, armed, Modifier.matchParentSize())
        }
        Box(Modifier.offset { IntOffset(offset.floatValue.roundToInt(), 0) }) {
            content()
        }
    }
}

/** The track behind a swiped row: the action's icon in a circle, and its name. */
@Composable
private fun SwipeTrack(visual: SwipeVisual, towardsStart: Boolean, armed: Boolean, modifier: Modifier = Modifier) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val circle by animateColorAsState(
        when {
            visual.destructive -> if (armed) colors.critical else colors.critical.copy(alpha = 0.55f)
            else -> if (armed) colors.accentContainerStrong else colors.accentContainer
        },
        BrMotion.quick(),
        label = "swipeCircle",
    )
    val iconTint = if (visual.destructive) colors.onInk else colors.accentSoft
    val labelColor by animateColorAsState(
        if (armed) colors.textPrimary else colors.textSecondary,
        BrMotion.quick(),
        label = "swipeLabel",
    )
    val scale by animateFloatAsState(if (armed) 1f else 0.9f, BrMotion.quick(), label = "swipeScale")
    Row(
        modifier
            .background(colors.surfaceRaised)
            .padding(horizontal = BrSpacing.Gutter),
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.M, if (towardsStart) Alignment.End else Alignment.Start),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (towardsStart) Text(visual.label, style = type.label, color = labelColor)
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .clip(CircleShape)
                .background(circle),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(visual.icon), contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        if (!towardsStart) Text(visual.label, style = type.label, color = labelColor)
    }
}

/** The first sync's progress: a quiet label, a tabular percentage and a slim bar. */
@Composable
private fun SyncProgress(visible: Boolean, progress: Float, label: String, modifier: Modifier = Modifier) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = BrTransitions.expandIn,
        exit = BrTransitions.collapseOut,
    ) {
        val colors = BedrockTheme.colors
        val type = BedrockTheme.typography
        val clamped = progress.coerceIn(0f, 1f)
        val percent = (clamped * 100).roundToInt()
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.XS)
                .semantics {
                    contentDescription = "$label, $percent percent"
                    progressBarRangeInfo = ProgressBarRangeInfo(clamped, 0f..1f)
                },
            verticalArrangement = Arrangement.spacedBy(BrSpacing.S),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = type.caption, color = colors.textTertiary)
                Text("$percent%", style = type.caption.copy(fontFeatureSettings = "tnum"), color = colors.textTertiary)
            }
            BrProgressBar(progress = clamped)
        }
    }
}

@Composable
private fun FilterRow(selected: InboxFilter, onIntent: (ConversationsIntent) -> Unit) {
    Row(
        Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.XS)
            .padding(bottom = BrSpacing.S),
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
    ) {
        InboxFilter.entries.forEachIndexed { index, filter ->
            BrChip(
                label = FilterLabels.getValue(filter),
                selected = filter == selected,
                onClick = { onIntent(ConversationsIntent.FilterSelected(filter)) },
                modifier = Modifier.revealOnEnter(index),
            )
        }
    }
}

@Composable
private fun AccessPrompt(title: String, collapse: State<Float>, onIntent: (ConversationsIntent) -> Unit) {
    Column(
        Modifier
            .glassSource()
            .fillMaxSize()
            .padding(top = topBarClearance(), bottom = brNavClearance()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrLargeTitle(title = title, collapse = collapse)
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            EmptyState(
                icon = painterResource(R.drawable.ic_lock),
                title = "See your messages",
                message = "Allow access to read the SMS and MMS already on this phone. Nothing leaves your device.",
            )
        }
        BrButton(
            text = "Allow access",
            onClick = { onIntent(ConversationsIntent.GrantAccessClicked) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BrSpacing.Gutter, vertical = BrSpacing.XL),
        )
    }
}
