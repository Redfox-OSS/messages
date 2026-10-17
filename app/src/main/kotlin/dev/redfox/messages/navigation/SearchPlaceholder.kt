package dev.redfox.messages.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavKey
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.DetailPage
import dev.redfox.obsidian.components.EmptyState
import kotlinx.serialization.Serializable

@Serializable
data object SearchKey : NavKey

/** Stand-in for message search, which comes next. */
@Composable
fun SearchPlaceholder(onBack: () -> Unit) {
    DetailPage(title = "Search", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            EmptyState(
                icon = R.drawable.ic_search,
                title = "Search is on its way",
                subtitle = "Soon you'll find any message, person or link from here.",
            )
        }
    }
}
