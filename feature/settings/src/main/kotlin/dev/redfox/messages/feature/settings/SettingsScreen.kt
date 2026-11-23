package dev.redfox.messages.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.redfox.bedrock.components.BrLargeTitle
import dev.redfox.bedrock.components.BrMenu
import dev.redfox.bedrock.components.BrMenuItem
import dev.redfox.bedrock.components.BrTopBar
import dev.redfox.bedrock.components.IconTile
import dev.redfox.bedrock.components.ListGroup
import dev.redfox.bedrock.components.ListRow
import dev.redfox.bedrock.components.SectionHeader
import dev.redfox.bedrock.components.rememberTopBarCollapse
import dev.redfox.bedrock.components.topBarClearance
import dev.redfox.bedrock.effects.glassSource
import dev.redfox.bedrock.effects.pressable
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.RevealColumn
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.model.VibrationPattern
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.theme.toBedrock
import kotlinx.serialization.Serializable

@Serializable
data object SettingsKey : NavKey

@Composable
fun SettingsRoute(
    versionLabel: String,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(SettingsIntent.Refresh) }
    SettingsScreen(state = state, versionLabel = versionLabel, onBack = onBack, onIntent = viewModel::dispatch)
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    versionLabel: String,
    onBack: () -> Unit,
    onIntent: (SettingsIntent) -> Unit,
) {
    val scrollState = rememberScrollState()
    val collapse = rememberTopBarCollapse(scrollState)
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() + BrSpacing.XXL
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .glassSource()
                .verticalScroll(scrollState)
                .padding(top = topBarClearance(), bottom = bottom),
        ) {
            BrLargeTitle(title = "Settings", collapse = collapse)
            RevealColumn(
                modifier = Modifier.fillMaxWidth().padding(horizontal = BrSpacing.Gutter),
                verticalArrangement = Arrangement.spacedBy(BrSpacing.Section - BrSpacing.S),
            ) {
                Section("Appearance") {
                    AccentPicker(
                        selected = state.accent,
                        onSelect = { onIntent(SettingsIntent.AccentSelected(it)) },
                    )
                }
                Section("Conversations") {
                    ChoiceRow(
                        title = "Swipe left",
                        icon = R.drawable.ic_swipe_left,
                        selected = state.swipeLeft,
                        options = SwipeAction.entries,
                        label = { it.label },
                        divider = true,
                        onSelect = { onIntent(SettingsIntent.SwipeActionSelected(left = true, action = it)) },
                    )
                    ChoiceRow(
                        title = "Swipe right",
                        icon = R.drawable.ic_swipe_right,
                        selected = state.swipeRight,
                        options = SwipeAction.entries,
                        label = { it.label },
                        onSelect = { onIntent(SettingsIntent.SwipeActionSelected(left = false, action = it)) },
                    )
                }
                Section("Notifications") {
                    ChoiceRow(
                        title = "Vibration",
                        icon = R.drawable.ic_notifications,
                        selected = state.vibration,
                        options = VibrationPattern.entries,
                        label = { it.label },
                        onSelect = { onIntent(SettingsIntent.VibrationSelected(it)) },
                    )
                }
                Section("Setup") {
                    ListRow(
                        title = "Default SMS app",
                        subtitle = if (state.isDefaultSmsApp) "Messages is the default" else "Another app receives texts",
                        leading = { IconTile(painterResource(R.drawable.ic_sms)) },
                    )
                }
                Section("Developer") {
                    ListRow(
                        title = "Version",
                        subtitle = versionLabel,
                        leading = { IconTile(painterResource(R.drawable.ic_check_circle)) },
                    )
                }
            }
        }
        BrTopBar(
            title = "Settings",
            collapse = collapse,
            modifier = Modifier.align(Alignment.TopCenter),
            onBack = onBack,
        )
    }
}

/** A titled group of rows. (SectionHeader carries its own top padding, so the gap reads as 40dp.) */
@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column {
        SectionHeader(title)
        ListGroup { content() }
    }
}

/** The accent row: its name, then one swatch per accent, the chosen one ringed. */
@Composable
private fun AccentPicker(selected: AppAccent, onSelect: (AppAccent) -> Unit) {
    Column {
        ListRow(
            title = "Accent",
            subtitle = selected.name,
            leading = { IconTile(painterResource(R.drawable.ic_palette)) },
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = BrSpacing.S, end = BrSpacing.S, bottom = BrSpacing.M)
                .selectableGroup(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppAccent.entries.forEach { accent ->
                AccentSwatch(
                    color = accent.toBedrock().main,
                    name = accent.name,
                    selected = accent == selected,
                    onClick = { onSelect(accent) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One accent choice: a round swatch. When chosen, a calm 2dp ring eases in around it while the
 * swatch settles a little smaller inside the ring. No pop, no glow.
 */
@Composable
private fun AccentSwatch(
    color: Color,
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringColor = BedrockTheme.colors.textPrimary
    val ring = animateFloatAsState(if (selected) 1f else 0f, BrMotion.settle(), label = "swatchRing")
    Box(
        modifier
            .height(52.dp)
            .pressable(role = Role.RadioButton, pressedScale = 0.94f, haptic = HapticFeedbackType.SegmentTick, onClick = onClick)
            .semantics {
                contentDescription = "$name accent"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer { alpha = ring.value }
                .border(2.dp, ringColor.copy(alpha = 0.85f), CircleShape)
        )
        Box(
            Modifier
                .size(40.dp)
                .graphicsLayer {
                    val s = 1f - 0.16f * ring.value
                    scaleX = s
                    scaleY = s
                }
                .clip(CircleShape)
                .background(color)
        )
    }
}

/** A settings row whose value is picked from a menu. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    @DrawableRes icon: Int,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    divider: Boolean = false,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ListRow(
            title = title,
            subtitle = label(selected),
            leading = { IconTile(painterResource(icon)) },
            chevron = true,
            divider = divider,
            onClick = { open = true },
        )
        BrMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                BrMenuItem(
                    text = label(option),
                    selected = option == selected,
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}
