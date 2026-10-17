package dev.redfox.messages.core.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter

/** How one swipe direction looks: what's revealed behind the row. */
data class SwipeVisual(val label: String, @DrawableRes val icon: Int, val color: Color)

/**
 * Gmail-style swipe on a list row (stock [SwipeToDismissBox] wrapped per PLAN.md §7.2). Past
 * the threshold the action fires and the row springs back; the list itself then removes or
 * updates the row (archive, delete…). A null visual disables that direction.
 */
@Composable
fun SwipeActionRow(
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
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.EndToStart -> latestLeft()
                SwipeToDismissBoxValue.StartToEnd -> latestRight()
                SwipeToDismissBoxValue.Settled -> return@rememberSwipeToDismissBoxState true
            }
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            false // Spring back; the action's own result (row gone, badge changed) shows it worked.
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        enableDismissFromStartToEnd = right != null,
        enableDismissFromEndToStart = left != null,
        backgroundContent = {
            val towardsStart = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            val visual = (if (towardsStart) left else right) ?: return@SwipeToDismissBox
            val armed = state.targetValue != SwipeToDismissBoxValue.Settled
            val iconScale by animateFloatAsState(if (armed) 1.15f else 0.9f, label = "swipeIcon")
            Row(
                Modifier
                    .fillMaxSize()
                    .background(if (armed) visual.color else visual.color.copy(alpha = 0.55f))
                    .padding(horizontal = ScreenGutter),
                horizontalArrangement = Arrangement.spacedBy(ObSpacing.S, if (towardsStart) Alignment.End else Alignment.Start),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(painterResource(visual.icon), contentDescription = null, tint = Color.White, modifier = Modifier.scale(iconScale))
                Text(visual.label, style = ObsidianTheme.typography.labelStrong, color = Color.White)
            }
        },
    ) {
        content()
    }
}
