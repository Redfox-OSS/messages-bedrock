package dev.redfox.messages.feature.thread

import android.Manifest
import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemContentType
import androidx.paging.compose.itemKey
import dev.redfox.bedrock.components.BrDialog
import dev.redfox.bedrock.components.BrIconButton
import dev.redfox.bedrock.components.BrSheet
import dev.redfox.bedrock.components.BrTopBar
import dev.redfox.bedrock.components.IconButtonVariant
import dev.redfox.bedrock.components.SheetAction
import dev.redfox.bedrock.components.topBarClearance
import dev.redfox.bedrock.effects.glass
import dev.redfox.bedrock.effects.glassSource
import dev.redfox.bedrock.motion.revealOnEnter
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrShapes
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.model.OutgoingAttachment
import dev.redfox.messages.core.model.ScheduledMessage
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.BubblePosition
import dev.redfox.messages.core.ui.components.ComposerBar
import dev.redfox.messages.core.ui.components.ComposerNotice
import dev.redfox.messages.core.ui.components.MessageBubble
import dev.redfox.messages.core.ui.components.RichCardBubble
import dev.redfox.messages.core.ui.components.SchedulePicker
import dev.redfox.messages.core.ui.components.formatScheduleTime
import dev.redfox.messages.core.ui.format.TimeFormat
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.messages.core.ui.platform.VoiceNote
import dev.redfox.messages.core.ui.platform.VoiceRecorder
import dev.redfox.messages.core.ui.platform.rememberCaller
import kotlinx.serialization.Serializable

/**
 * A conversation. [addresses] and [title] are set when it's opened for someone new (from the
 * recipient picker or another app's "send SMS" link), before the thread has any messages;
 * [draft] pre-fills the composer.
 */
@Serializable
data class ThreadKey(
    val threadId: Long,
    val addresses: List<String> = emptyList(),
    val title: String? = null,
    val draft: String? = null,
) : NavKey

private const val MAX_PICK = 5

/** Share of the MMS limit a voice note may use (the rest: headers, text). */
private const val VOICE_SHARE = 0.85

/** Only the newest screenful plays the entrance; older history just appears. */
private const val RevealedRows = 12

@Composable
fun ThreadRoute(
    key: ThreadKey,
    onBack: () -> Unit,
    viewModel: ThreadViewModel = hiltViewModel<ThreadViewModel, ThreadViewModel.Factory>(
        creationCallback = { factory -> factory.create(key.threadId, key.addresses, key.title, key.draft) }
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val rows = viewModel.rows.collectAsLazyPagingItems()
    val listState = rememberLazyListState()
    var scrollToSent by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val call = rememberCaller()
    val photos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(MAX_PICK)) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.dispatch(
                ThreadIntent.AddAttachments(
                    uris.map { OutgoingAttachment(it.toString(), context.contentResolver.getType(it) ?: "image/jpeg") }
                )
            )
        }
    }
    val voice = rememberVoiceNoteRecorder(
        maxBytes = { (state.maxMmsBytes * VOICE_SHARE).toInt() },
        onRecorded = { note ->
            viewModel.dispatch(
                ThreadIntent.AddAttachments(listOf(OutgoingAttachment(note.uri.toString(), note.mimeType, durationMillis = note.durationMillis)))
            )
        },
    )
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ThreadEffect.OpenUrl -> runCatching { uriHandler.openUri(effect.url) }
            is ThreadEffect.Dial -> runCatching {
                // ACTION_DIAL only pre-fills the dialler: no call permission, the user confirms.
                context.startActivity(Intent(Intent.ACTION_DIAL, "tel:${effect.phoneNumber}".toUri()))
            }
            // The sent message isn't in the list yet; FollowNewest scrolls once it lands.
            ThreadEffect.ScrollToNewest -> scrollToSent = true
            is ThreadEffect.CopyText -> context.copyToClipboard("Message", effect.text)
            is ThreadEffect.CopyOtp -> context.copyToClipboard("OTP", effect.code, sensitive = true)
            is ThreadEffect.Call -> call(effect.phoneNumber)
            is ThreadEffect.ShowError -> Toast.makeText(context, effect.message, Toast.LENGTH_LONG).show()
        }
    }
    FollowNewest(rows, listState, scrollToSent = { scrollToSent }, onScrolled = { scrollToSent = false })
    ThreadScreen(
        state = state,
        rows = rows,
        listState = listState,
        timeFormat = viewModel.timeFormat,
        onBack = onBack,
        onIntent = viewModel::dispatch,
        recordingMillis = voice.elapsedMillis,
        onAttachPhotos = { photos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
        onRecord = voice.start,
        onStopRecording = voice.stop,
        onCancelRecording = voice.cancel,
    )
}

@Composable
fun ThreadScreen(
    state: ThreadState,
    rows: LazyPagingItems<ThreadRow>,
    listState: LazyListState,
    timeFormat: TimeFormat,
    onBack: () -> Unit,
    onIntent: (ThreadIntent) -> Unit,
    recordingMillis: Long? = null,
    onAttachPhotos: () -> Unit = {},
    onRecord: () -> Unit = {},
    onStopRecording: () -> Unit = {},
    onCancelRecording: () -> Unit = {},
) {
    val density = LocalDensity.current
    // The composer floats over the list; the list's end padding follows its real height.
    var composerHeight by remember { mutableIntStateOf(0) }
    // A chat starts at the bottom and has no large title: the bar is always frosted, title showing.
    val collapse = remember { mutableFloatStateOf(1f) }

    Box(Modifier.fillMaxSize()) {
        // The keyboard pushes list and composer up together.
        Box(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.glassSource().fillMaxSize(),
                // Reversed: "top" padding sits under the top bar, "bottom" above the composer.
                contentPadding = PaddingValues(
                    top = topBarClearance(),
                    bottom = with(density) { composerHeight.toDp() } + BrSpacing.S,
                ),
            ) {
                items(
                    count = rows.itemCount,
                    key = rows.itemKey { it.key },
                    contentType = rows.itemContentType { it::class },
                ) { index ->
                    val row = rows[index] ?: return@items
                    val reveal = if (index < RevealedRows) Modifier.revealOnEnter(index, offsetY = 12.dp) else Modifier
                    when (row) {
                        is ThreadRow.Day -> DateDivider(row.label, reveal)
                        is ThreadRow.Msg -> {
                            // Neighbours via peek(): reads what's loaded without triggering more paging.
                            // reverseLayout: index + 1 is the older row (above), index - 1 the newer (below).
                            val layout = bubbleLayout(
                                older = (rows.peekOrNull(index + 1) as? ThreadRow.Msg)?.message,
                                message = row.message,
                                newer = (rows.peekOrNull(index - 1) as? ThreadRow.Msg)?.message,
                                isGroup = state.isGroup,
                                format = timeFormat,
                            )
                            val rich = row.message.rich
                            if (rich != null) RichCardBubble(
                                rich = rich,
                                timeLabel = layout.timeLabel,
                                onSuggestion = { onIntent(ThreadIntent.SuggestionClicked(it)) },
                                modifier = reveal.padding(top = BrSpacing.S),
                            ) else MessageBubble(
                                message = row.message,
                                position = layout.position,
                                timeLabel = layout.timeLabel,
                                showSenderName = layout.showSender,
                                onRetry = { onIntent(ThreadIntent.Retry(row.message)) },
                                brandLinks = row.brandLinks,
                                onOpenBrandLink = { onIntent(ThreadIntent.OpenBrandLink(it)) },
                                isStarred = row.message.key in state.starredKeys,
                                onLongPress = { onIntent(ThreadIntent.MessageLongPressed(row.message)) },
                                onRetryDownload = { onIntent(ThreadIntent.RetryDownload(row.message)) },
                                otp = row.otp,
                                onCopyOtp = { onIntent(ThreadIntent.CopyOtp(it)) },
                                modifier = reveal.padding(
                                    // Tight gap inside a run, a breath between runs.
                                    top = if (layout.position == BubblePosition.Middle || layout.position == BubblePosition.Last) BrSpacing.XXS
                                    else BrSpacing.S,
                                ),
                            )
                        }
                    }
                }
            }

            val composerModifier = Modifier
                .align(Alignment.BottomCenter)
                .onSizeChanged { composerHeight = it.height }
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(bottom = BrSpacing.S)
            if (state.canReply) Column(composerModifier, verticalArrangement = Arrangement.spacedBy(BrSpacing.XS)) {
                state.scheduled.forEach { scheduled ->
                    ScheduledStrip(
                        scheduled = scheduled,
                        onSendNow = { onIntent(ThreadIntent.SendScheduledNow(scheduled.id)) },
                        onCancel = { onIntent(ThreadIntent.CancelScheduled(scheduled.id)) },
                    )
                }
                ComposerBar(
                    text = state.draft,
                    onTextChange = { onIntent(ThreadIntent.DraftChanged(it)) },
                    onSend = { onIntent(ThreadIntent.Send) },
                    canSend = state.canSend,
                    sim = state.selectedSim,
                    showSim = state.showSimPicker,
                    onSimClick = { onIntent(ThreadIntent.SwitchSim) },
                    counter = state.counter,
                    onSendLongPress = { onIntent(ThreadIntent.ScheduleClicked) },
                    placeholder = if (state.sendsAsMms) "MMS message" else "Text message",
                    attachments = state.attachments,
                    onRemoveAttachment = { onIntent(ThreadIntent.RemoveAttachment(it)) },
                    onAttachClick = onAttachPhotos,
                    onMicClick = onRecord,
                    recordingMillis = recordingMillis,
                    onStopRecording = onStopRecording,
                    onCancelRecording = onCancelRecording,
                )
            } else if (state.recipients.isNotEmpty()) {
                ComposerNotice("This sender doesn't accept replies", composerModifier)
            }
        }

        BrTopBar(
            title = state.title,
            collapse = collapse,
            modifier = Modifier.align(Alignment.TopCenter),
            onBack = onBack,
        ) {
            if (state.callableNumber != null) {
                BrIconButton(
                    painterResource(R.drawable.ic_call),
                    contentDescription = "Call",
                    onClick = { onIntent(ThreadIntent.CallClicked) },
                    variant = IconButtonVariant.Glass,
                )
            }
        }
    }

    // The sheet stays composed so it can glide out; it keeps showing the last message while it does.
    val lastActions = remember { arrayOfNulls<Message>(1) }
    val sheetMessage = state.actionsFor?.also { lastActions[0] = it } ?: lastActions[0]
    BrSheet(visible = state.actionsFor != null, onDismiss = { onIntent(ThreadIntent.DismissActions) }) {
        if (sheetMessage != null) {
            if (sheetMessage.body.isNotEmpty()) {
                SheetAction(
                    "Copy text",
                    onClick = { onIntent(ThreadIntent.CopyMessage) },
                    icon = painterResource(R.drawable.ic_content_copy),
                )
            }
            val starred = sheetMessage.key in state.starredKeys
            SheetAction(
                if (starred) "Unstar" else "Star",
                onClick = { onIntent(ThreadIntent.ToggleStar) },
                icon = painterResource(if (starred) R.drawable.ic_star_filled else R.drawable.ic_star),
            )
            SheetAction(
                "Delete",
                onClick = { onIntent(ThreadIntent.DeleteMessage) },
                icon = painterResource(R.drawable.ic_delete),
                destructive = true,
            )
        }
    }
    BrDialog(
        visible = state.confirmDelete != null,
        onDismiss = { onIntent(ThreadIntent.CancelDelete) },
        title = "Delete this message?",
        message = "It's removed from this phone for good.",
        confirmLabel = "Delete",
        onConfirm = { onIntent(ThreadIntent.ConfirmDelete) },
        destructive = true,
    )
    SchedulePicker(
        onPick = { onIntent(ThreadIntent.ScheduleAt(it)) },
        onDismiss = { onIntent(ThreadIntent.DismissSchedule) },
        visible = state.showSchedulePicker,
    )
}

/** The day above a run of messages: quiet centred text, no rules. */
@Composable
private fun DateDivider(label: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(vertical = BrSpacing.M), contentAlignment = Alignment.Center) {
        Text(label, style = BedrockTheme.typography.caption, color = BedrockTheme.colors.textTertiary)
    }
}

/** A scheduled message waiting above the composer: when, a preview, send now or cancel. */
@Composable
private fun ScheduledStrip(scheduled: ScheduledMessage, onSendNow: () -> Unit, onCancel: () -> Unit) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val shape = BrShapes.Large
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = BrSpacing.Gutter - BrSpacing.XXS)
            .glass(shape)
            .then(if (scheduled.failed) Modifier.border(1.dp, colors.critical.copy(alpha = 0.5f), shape) else Modifier)
            .padding(start = BrSpacing.L, end = BrSpacing.XS, top = BrSpacing.XS, bottom = BrSpacing.XS),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
    ) {
        Icon(
            painterResource(R.drawable.ic_schedule_send),
            contentDescription = null,
            tint = if (scheduled.failed) colors.critical else colors.accentSoft,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                if (scheduled.failed) "Couldn't send · tap Send now to retry" else formatScheduleTime(scheduled.sendAt),
                style = type.label.copy(fontFeatureSettings = "tnum"),
                color = if (scheduled.failed) colors.critical else colors.accentSoft,
            )
            Text(scheduled.body, style = type.caption, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        BrIconButton(
            painterResource(R.drawable.ic_send),
            contentDescription = "Send now",
            onClick = onSendNow,
            variant = IconButtonVariant.Plain,
            tint = colors.textSecondary,
        )
        BrIconButton(
            painterResource(R.drawable.ic_close),
            contentDescription = "Cancel scheduled message",
            onClick = onCancel,
            variant = IconButtonVariant.Plain,
            tint = colors.textSecondary,
        )
    }
}

/** Puts [text] on the clipboard; [sensitive] keeps it out of the clipboard preview (one-time codes). */
private fun Context.copyToClipboard(label: String, text: String, sensitive: Boolean = false) {
    val clip = ClipData.newPlainText(label, text)
    if (sensitive) {
        // The constant arrived in Android 13; Android 12 reads the same key.
        val key = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ClipDescription.EXTRA_IS_SENSITIVE else "android.content.extra.IS_SENSITIVE"
        clip.description.extras = PersistableBundle().apply { putBoolean(key, true) }
    }
    getSystemService(ClipboardManager::class.java).setPrimaryClip(clip)
}

/**
 * Keeps the newest message in view when one arrives. A new message is inserted *before* the
 * first visible item, and LazyColumn holds that item still, so without this the new bubble
 * lands just below the screen. Follows when the user just sent something, or was already at
 * the bottom; someone reading older history isn't yanked down.
 */
@Composable
private fun FollowNewest(
    rows: LazyPagingItems<ThreadRow>,
    listState: LazyListState,
    scrollToSent: () -> Boolean,
    onScrolled: () -> Unit,
) {
    val currentScrollToSent by rememberUpdatedState(scrollToSent)
    val currentOnScrolled by rememberUpdatedState(onScrolled)
    LaunchedEffect(rows, listState) {
        var newest: String? = null
        snapshotFlow { if (rows.itemCount > 0) rows.peek(0)?.key else null }
            .collect { key ->
                val arrived = key != null && key != newest
                val atBottom = listState.firstVisibleItemIndex <= 1
                if (arrived && (currentScrollToSent() || (newest != null && atBottom))) {
                    listState.animateScrollToItem(0)
                    currentOnScrolled()
                }
                newest = key
            }
    }
}

/** The voice-note recorder for a screen: asks for the mic once, ticks the timer, cleans up on leave. */
private class VoiceNoteController(
    val elapsedMillis: Long?,
    val start: () -> Unit,
    val stop: () -> Unit,
    val cancel: () -> Unit,
)

@Composable
private fun rememberVoiceNoteRecorder(maxBytes: () -> Int, onRecorded: (VoiceNote) -> Unit): VoiceNoteController {
    val context = LocalContext.current
    val recorder = remember { VoiceRecorder(context.applicationContext) }
    var recording by remember { mutableStateOf(false) }
    var elapsed by remember { mutableStateOf(0L) }
    val latestOnRecorded by rememberUpdatedState(onRecorded)
    val latestMaxBytes by rememberUpdatedState(maxBytes)

    fun stop() {
        recording = false
        recorder.stop()?.let(latestOnRecorded)
    }
    fun begin() {
        if (recorder.start(latestMaxBytes()) { stop() }) recording = true
        else Toast.makeText(context, "Couldn't use the microphone", Toast.LENGTH_SHORT).show()
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) begin() else Toast.makeText(context, "Allow the microphone to record voice messages", Toast.LENGTH_LONG).show()
    }
    LaunchedEffect(recording) {
        while (recording) {
            elapsed = recorder.elapsedMillis
            kotlinx.coroutines.delay(200)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.cancel() } }

    return VoiceNoteController(
        elapsedMillis = if (recording) elapsed else null,
        start = {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) begin()
            else permission.launch(Manifest.permission.RECORD_AUDIO)
        },
        stop = ::stop,
        cancel = {
            recording = false
            recorder.cancel()
        },
    )
}

private fun <T : Any> LazyPagingItems<T>.peekOrNull(index: Int): T? =
    if (index in 0 until itemCount) peek(index) else null
