package dev.redfox.messages.feature.scheduled

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.TabPage
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.effects.glassSource
import kotlinx.serialization.Serializable

@Serializable
data object ScheduledKey : NavKey

/** Scheduled messages shell (M0). */
@Composable
fun ScheduledRoute() {
    TabPage(title = "Scheduled") { padding ->
        Box(Modifier.glassSource().fillMaxSize().padding(padding)) {
            EmptyState(
                icon = R.drawable.ic_schedule,
                title = "No scheduled messages",
                subtitle = "Long-press send in any conversation to send it later.",
            )
        }
    }
}
