package dev.redfox.messages.saved

import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import dev.redfox.obsidian.components.TabPage
import dev.redfox.messages.feature.scheduled.ScheduledPage
import dev.redfox.messages.feature.starred.StarredPage
import dev.redfox.obsidian.components.SegmentOption
import dev.redfox.obsidian.components.SegmentedPill
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data object SavedKey : NavKey

private val Segments = listOf(SegmentOption("Starred"), SegmentOption("Scheduled"))

/** Height of the Starred / Scheduled toggle row, so each page's list starts below it. */
private val SegmentRow = 52.dp

/**
 * The Saved tab: messages kept for later, starred and scheduled. One segmented toggle under the
 * title drives a two-page pager, and swiping the pages moves the toggle: tap or swipe, both
 * land in the same place with the same spring.
 */
@Composable
fun SavedRoute(onOpenThread: (threadId: Long) -> Unit) {
    val pager = rememberPagerState(pageCount = { Segments.size })
    val scope = rememberCoroutineScope()

    TabPage(title = "Saved") { padding ->
        val pagePadding = PaddingValues(
            top = padding.calculateTopPadding() + SegmentRow,
            bottom = padding.calculateBottomPadding(),
        )
        Box(Modifier.fillMaxSize()) {
            HorizontalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1, // The other page is ready before you swipe to it.
            ) { page ->
                when (page) {
                    0 -> StarredPage(contentPadding = pagePadding, onOpenThread = onOpenThread)
                    else -> ScheduledPage(contentPadding = pagePadding, onOpenThread = onOpenThread)
                }
            }
            SegmentedPill(
                options = Segments,
                // The target page, so the toggle starts moving as soon as a swipe commits.
                selectedIndex = pager.targetPage,
                onSelect = { index ->
                    scope.launch { pager.animateScrollToPage(index, animationSpec = spring(dampingRatio = 0.85f, stiffness = 380f)) }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = padding.calculateTopPadding() - ObSpacing.XS, start = ScreenGutter, end = ScreenGutter),
            )
        }
    }
}
