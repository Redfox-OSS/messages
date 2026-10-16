package dev.redfox.messages.core.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource

/** Ignore tiny deltas so slow drags and jitter don't make the bar flicker. */
private const val ScrollThresholdPx = 3f

/**
 * Observes every vertical scroll beneath it (attach the connection with
 * `Modifier.nestedScroll` on the screen container) and reports whether the nav bar should be
 * compact: true while content moves toward its end (finger swiping up), false when it moves
 * back, overscrolls at the top, or [resetKey] (the current destination) changes.
 *
 * Consumes nothing, so lists behave exactly as before; new screens get the behaviour for free.
 */
@Composable
fun rememberCompactNavState(resetKey: Any?): Pair<State<Boolean>, NestedScrollConnection> {
    val compact = remember { mutableStateOf(false) }
    LaunchedEffect(resetKey) { compact.value = false }

    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // Only y is read, so horizontal swipes (filter chips) never flip the bar.
                if (available.y < -ScrollThresholdPx) compact.value = true
                else if (available.y > ScrollThresholdPx) compact.value = false
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                // Leftover downward delta = the list is already at its top: make sure it's expanded.
                if (available.y > 0f) compact.value = false
                return Offset.Zero
            }
        }
    }
    return compact to connection
}
