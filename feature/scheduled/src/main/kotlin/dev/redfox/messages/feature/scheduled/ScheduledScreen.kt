package dev.redfox.messages.feature.scheduled

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.ui.text.style.TextOverflow
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.redfox.messages.core.domain.usecase.ManageScheduledUseCase
import dev.redfox.messages.core.domain.usecase.ObserveScheduledUseCase
import dev.redfox.messages.core.model.ScheduledMessage
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.SchedulePicker
import dev.redfox.messages.core.ui.components.formatScheduleTime
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.messages.core.ui.mvi.MviViewModel
import dev.redfox.messages.core.ui.mvi.UiEffect
import dev.redfox.messages.core.ui.mvi.UiIntent
import dev.redfox.messages.core.ui.mvi.UiState
import dev.redfox.bedrock.components.BrCard
import dev.redfox.bedrock.components.BrDialog
import dev.redfox.bedrock.components.BrIconButton
import dev.redfox.bedrock.components.EmptyState
import dev.redfox.bedrock.components.IconButtonVariant
import dev.redfox.bedrock.effects.glassSource
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.revealOnEnter
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

@Immutable
data class ScheduledState(
    val items: ImmutableList<ScheduledMessage> = persistentListOf(),
    val loaded: Boolean = false,
    /** The message whose time is being changed. */
    val rescheduling: ScheduledMessage? = null,
    val confirmCancel: ScheduledMessage? = null,
) : UiState

sealed interface ScheduledIntent : UiIntent {
    data class Open(val message: ScheduledMessage) : ScheduledIntent
    data class SendNow(val message: ScheduledMessage) : ScheduledIntent
    data class ChangeTime(val message: ScheduledMessage) : ScheduledIntent
    data class TimePicked(val sendAt: Long) : ScheduledIntent
    data object DismissPicker : ScheduledIntent
    data class Cancel(val message: ScheduledMessage) : ScheduledIntent
    data object ConfirmCancel : ScheduledIntent
    data object KeepScheduled : ScheduledIntent
}

sealed interface ScheduledEffect : UiEffect {
    data class OpenThread(val threadId: Long) : ScheduledEffect
}

/** Every scheduled message, soonest first, with send-now / change time / cancel. */
@HiltViewModel
class ScheduledViewModel @Inject constructor(
    observeScheduled: ObserveScheduledUseCase,
    private val manage: ManageScheduledUseCase,
) : MviViewModel<ScheduledState, ScheduledIntent, ScheduledEffect>(ScheduledState()) {

    init {
        observeScheduled().bind { copy(items = it.toImmutableList(), loaded = true) }
    }

    override suspend fun handle(intent: ScheduledIntent) {
        when (intent) {
            is ScheduledIntent.Open -> emit(ScheduledEffect.OpenThread(intent.message.threadId))
            is ScheduledIntent.SendNow -> manage.sendNow(intent.message.id)
            is ScheduledIntent.ChangeTime -> reduce { copy(rescheduling = intent.message) }
            ScheduledIntent.DismissPicker -> reduce { copy(rescheduling = null) }
            is ScheduledIntent.TimePicked -> {
                val message = state.value.rescheduling ?: return
                reduce { copy(rescheduling = null) }
                manage.reschedule(message.id, intent.sendAt)
            }
            is ScheduledIntent.Cancel -> reduce { copy(confirmCancel = intent.message) }
            ScheduledIntent.KeepScheduled -> reduce { copy(confirmCancel = null) }
            ScheduledIntent.ConfirmCancel -> {
                val message = state.value.confirmCancel ?: return
                reduce { copy(confirmCancel = null) }
                manage.cancel(message.id)
            }
        }
    }
}

/** Scheduled messages as a page of the Saved tab: the list only, under the caller's header. */
@Composable
fun ScheduledPage(
    contentPadding: PaddingValues,
    onOpenThread: (threadId: Long) -> Unit,
    viewModel: ScheduledViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is ScheduledEffect.OpenThread -> onOpenThread(effect.threadId)
        }
    }
    ScheduledScreen(state, contentPadding, viewModel::dispatch)
}

@Composable
fun ScheduledScreen(state: ScheduledState, padding: PaddingValues, onIntent: (ScheduledIntent) -> Unit) {
    run {
        if (state.loaded && state.items.isEmpty()) {
            Box(Modifier.glassSource().fillMaxSize().padding(padding)) {
                EmptyState(
                    icon = painterResource(R.drawable.ic_schedule),
                    title = "Nothing scheduled",
                    message = "Long-press the send button in a conversation to send a message later.",
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.glassSource().fillMaxSize(),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                    start = BrSpacing.Gutter,
                    end = BrSpacing.Gutter,
                ),
                verticalArrangement = Arrangement.spacedBy(BrSpacing.M),
            ) {
                itemsIndexed(state.items, key = { _, item -> item.id }) { index, message ->
                    ScheduledCard(
                        message = message,
                        onIntent = onIntent,
                        modifier = Modifier
                            .animateItem(placementSpec = BrMotion.settle(IntOffset.VisibilityThreshold))
                            .then(if (index < 10) Modifier.revealOnEnter(index) else Modifier),
                    )
                }
            }
        }
    }

    SchedulePicker(
        title = "Change time",
        onPick = { onIntent(ScheduledIntent.TimePicked(it)) },
        onDismiss = { onIntent(ScheduledIntent.DismissPicker) },
        visible = state.rescheduling != null,
    )
    // Always composed so it can ease out; it keeps naming the message while it leaves.
    var lastCancel by remember { mutableStateOf(state.confirmCancel) }
    if (state.confirmCancel != null) lastCancel = state.confirmCancel
    BrDialog(
        visible = state.confirmCancel != null,
        onDismiss = { onIntent(ScheduledIntent.KeepScheduled) },
        title = "Cancel scheduled message?",
        message = "The message to ${lastCancel?.title.orEmpty()} won't be sent.",
        confirmLabel = "Cancel message",
        onConfirm = { onIntent(ScheduledIntent.ConfirmCancel) },
        dismissLabel = "Keep",
        destructive = true,
    )
}

@Composable
private fun ScheduledCard(message: ScheduledMessage, onIntent: (ScheduledIntent) -> Unit, modifier: Modifier = Modifier) {
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    BrCard(modifier.fillMaxWidth(), onClick = { onIntent(ScheduledIntent.Open(message)) }) {
        Column(verticalArrangement = Arrangement.spacedBy(BrSpacing.S)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(message.title, style = type.label, color = colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        if (message.failed) "Couldn't send at ${formatScheduleTime(message.sendAt)}" else formatScheduleTime(message.sendAt),
                        style = type.caption.copy(fontFeatureSettings = "tnum"),
                        color = if (message.failed) colors.critical else colors.accentSoft,
                    )
                }
                BrIconButton(
                    painterResource(R.drawable.ic_send),
                    contentDescription = "Send now",
                    onClick = { onIntent(ScheduledIntent.SendNow(message)) },
                    variant = IconButtonVariant.Plain,
                    tint = colors.textSecondary,
                )
                BrIconButton(
                    painterResource(R.drawable.ic_schedule),
                    contentDescription = "Change time",
                    onClick = { onIntent(ScheduledIntent.ChangeTime(message)) },
                    variant = IconButtonVariant.Plain,
                    tint = colors.textSecondary,
                )
                BrIconButton(
                    painterResource(R.drawable.ic_delete),
                    contentDescription = "Cancel",
                    onClick = { onIntent(ScheduledIntent.Cancel(message)) },
                    variant = IconButtonVariant.Plain,
                    tint = colors.textSecondary,
                )
            }
            Text(message.body, style = type.body, color = colors.textPrimary, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
    }
}
