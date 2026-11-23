package dev.redfox.messages.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.redfox.bedrock.components.BrButton
import dev.redfox.bedrock.components.BrChip
import dev.redfox.bedrock.components.BrSheet
import dev.redfox.bedrock.components.SheetAction
import dev.redfox.bedrock.motion.BrTransitions
import dev.redfox.bedrock.theme.BedrockTheme
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.core.ui.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A quick pick in the schedule sheet ("Tomorrow, 9:00 am"). */
data class ScheduleOption(val label: String, val at: Long)

/**
 * The quick choices Google Messages offers: later today, tonight, tomorrow morning and evening.
 * Pure (takes the clock), so it's unit-tested; times already past are left out.
 */
fun scheduleOptions(now: LocalDateTime, zone: ZoneId): List<ScheduleOption> {
    val today = now.toLocalDate()
    fun at(date: LocalDate, hour: Int) = date.atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    val nowMillis = now.atZone(zone).toInstant().toEpochMilli()
    val inAnHour = now.plusHours(1).withSecond(0).withNano(0)
    return listOfNotNull(
        ScheduleOption("In 1 hour", inAnHour.atZone(zone).toInstant().toEpochMilli()),
        ScheduleOption("Later today, 6:00 pm", at(today, 18)).takeIf { it.at > nowMillis + 30 * 60_000 },
        ScheduleOption("Tonight, 9:00 pm", at(today, 21)).takeIf { it.at > nowMillis + 30 * 60_000 },
        ScheduleOption("Tomorrow, 9:00 am", at(today.plusDays(1), 9)),
        ScheduleOption("Tomorrow, 6:00 pm", at(today.plusDays(1), 18)),
    )
}

/**
 * Picks when to send: quick options, or any day this fortnight plus a time. Calls [onPick]
 * with epoch millis.
 *
 * [visible] drives the sheet, so it can stay composed and play its exit; callers that compose
 * it only while showing can leave it at `true`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchedulePicker(
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Schedule message",
    visible: Boolean = true,
) {
    val context = LocalContext.current
    val colors = BedrockTheme.colors
    val type = BedrockTheme.typography
    val zone = remember { ZoneId.systemDefault() }
    // Fresh times and the quick list each time the sheet opens (it may stay composed while hidden).
    val now = remember(visible) { LocalDateTime.now(zone) }
    val options = remember(now) { scheduleOptions(now, zone) }
    var custom by remember { mutableStateOf(false) }
    LaunchedEffect(visible) { if (visible) custom = false }

    BrSheet(visible = visible, onDismiss = onDismiss, title = title) {
        AnimatedContent(
            targetState = custom,
            transitionSpec = { BrTransitions.swap },
            label = "scheduleMode",
        ) { picking ->
            Column(Modifier.fillMaxWidth()) {
                if (!picking) {
                    options.forEach { option ->
                        SheetAction(
                            label = option.label,
                            onClick = { onPick(option.at) },
                            icon = painterResource(R.drawable.ic_schedule_send),
                        )
                    }
                    SheetAction(
                        label = "Pick date & time",
                        onClick = { custom = true },
                        icon = painterResource(R.drawable.ic_schedule),
                    )
                } else {
                    val is24h = remember { android.text.format.DateFormat.is24HourFormat(context) }
                    val defaultTime = now.plusHours(1)
                    val time = rememberTimePickerState(defaultTime.hour, 0, is24h)
                    var dayOffset by remember { mutableIntStateOf(0) }
                    val days = remember(now) { (0 until 14).map { now.toLocalDate().plusDays(it.toLong()) } }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(vertical = BrSpacing.S),
                        horizontalArrangement = Arrangement.spacedBy(BrSpacing.S),
                    ) {
                        days.forEachIndexed { index, day ->
                            BrChip(label = dayLabel(day, now.toLocalDate()), selected = dayOffset == index, onClick = { dayOffset = index })
                        }
                    }
                    Column(Modifier.fillMaxWidth().padding(top = BrSpacing.M), horizontalAlignment = Alignment.CenterHorizontally) {
                        TimePicker(
                            state = time,
                            colors = TimePickerDefaults.colors(
                                clockDialColor = colors.surfaceRaised,
                                clockDialSelectedContentColor = colors.onAccent,
                                clockDialUnselectedContentColor = colors.textPrimary,
                                selectorColor = colors.accentMain,
                                containerColor = Color.Transparent,
                                periodSelectorBorderColor = colors.line,
                                periodSelectorSelectedContainerColor = colors.accentContainerStrong,
                                periodSelectorUnselectedContainerColor = Color.Transparent,
                                periodSelectorSelectedContentColor = colors.textPrimary,
                                periodSelectorUnselectedContentColor = colors.textSecondary,
                                timeSelectorSelectedContainerColor = colors.accentContainerStrong,
                                timeSelectorUnselectedContainerColor = colors.surfaceRaised,
                                timeSelectorSelectedContentColor = colors.textPrimary,
                                timeSelectorUnselectedContentColor = colors.textSecondary,
                            ),
                        )
                    }
                    val pickedAt = days[dayOffset].atTime(LocalTime.of(time.hour, time.minute)).atZone(zone).toInstant().toEpochMilli()
                    val inFuture = pickedAt > System.currentTimeMillis() + 60_000
                    if (!inFuture) {
                        Text(
                            "Pick a time in the future",
                            style = type.labelSmall,
                            color = colors.critical,
                        )
                    }
                    BrButton(
                        text = "Schedule for ${formatScheduleTime(pickedAt)}",
                        onClick = { if (inFuture) onPick(pickedAt) },
                        modifier = Modifier.fillMaxWidth().padding(vertical = BrSpacing.L),
                        enabled = inFuture,
                    )
                }
            }
        }
    }
}

private fun dayLabel(day: LocalDate, today: LocalDate): String = when (day) {
    today -> "Today"
    today.plusDays(1) -> "Tomorrow"
    else -> "${day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${day.dayOfMonth}"
}

/** "Today, 9:00 pm" / "Tomorrow, 9:00 am" / "Sat 24 Oct, 6:00 pm". */
fun formatScheduleTime(at: Long, zone: ZoneId = ZoneId.systemDefault()): String {
    val time = Instant.ofEpochMilli(at).atZone(zone)
    val today = LocalDate.now(zone)
    val day = when (time.toLocalDate()) {
        today -> "Today"
        today.plusDays(1) -> "Tomorrow"
        else -> time.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    return "$day, " + time.format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())).lowercase()
}
