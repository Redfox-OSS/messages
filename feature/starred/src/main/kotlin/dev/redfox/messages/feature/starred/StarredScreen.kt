package dev.redfox.messages.feature.starred

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
data object StarredKey : NavKey

/** Starred messages shell (M0). */
@Composable
fun StarredRoute() {
    TabPage(title = "Starred") { padding ->
        Box(Modifier.glassSource().fillMaxSize().padding(padding)) {
            EmptyState(
                icon = R.drawable.ic_star,
                title = "Nothing starred",
                subtitle = "Long-press any message and star it to keep it here.",
            )
        }
    }
}
