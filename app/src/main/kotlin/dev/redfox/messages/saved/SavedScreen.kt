package dev.redfox.messages.saved

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation3.runtime.NavKey
import dev.redfox.bedrock.components.BrLargeTitle
import dev.redfox.bedrock.components.SegmentedControl
import dev.redfox.bedrock.components.topBarClearance
import dev.redfox.bedrock.motion.BrMotion
import dev.redfox.bedrock.motion.revealOnEnter
import dev.redfox.bedrock.navigation.brNavClearance
import dev.redfox.bedrock.theme.BrSpacing
import dev.redfox.messages.feature.scheduled.ScheduledPage
import dev.redfox.messages.feature.starred.StarredPage
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object SavedKey : NavKey

private val Segments = listOf("Starred", "Scheduled")

/**
 * The Saved tab: messages kept for later, starred and scheduled. The title and a segmented control
 * stay put while a two-page pager scrolls beneath them; tapping the control or swiping the pages
 * both land in the same place on the same calm spring.
 */
@Composable
fun SavedRoute(onOpenThread: (threadId: Long) -> Unit) {
    val pager = rememberPagerState(pageCount = { Segments.size })
    val scope = rememberCoroutineScope()
    // The title never scrolls away here: each page scrolls on its own below it.
    val collapse = remember { mutableFloatStateOf(0f) }
    val pagePadding = PaddingValues(top = BrSpacing.L, bottom = brNavClearance())

    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(topBarClearance()))
        BrLargeTitle(title = "Saved", collapse = collapse)
        SegmentedControl(
            options = Segments,
            // The target page, so the control starts moving as soon as a swipe commits.
            selectedIndex = pager.targetPage,
            onSelect = { index ->
                scope.launch { pager.animateScrollToPage(index, animationSpec = BrMotion.settle()) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = BrSpacing.Gutter)
                .revealOnEnter(1),
        )
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxWidth().weight(1f).graphicsLayer { clip = true },
            beyondViewportPageCount = 1, // The other page is ready before you swipe to it.
        ) { page ->
            when (page) {
                0 -> StarredPage(contentPadding = pagePadding, onOpenThread = onOpenThread)
                else -> ScheduledPage(contentPadding = pagePadding, onOpenThread = onOpenThread)
            }
        }
    }
}
