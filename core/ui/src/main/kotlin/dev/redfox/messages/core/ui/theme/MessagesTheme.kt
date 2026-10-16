package dev.redfox.messages.core.ui.theme

import androidx.compose.runtime.Composable
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.obsidian.theme.ObsidianAccent
import dev.redfox.obsidian.theme.ObsidianNeutrals
import dev.redfox.obsidian.theme.ObsidianTheme

/**
 * Thin adapter from the persisted domain [AppAccent] onto [ObsidianTheme]. Obsidian is always
 * dark; the neutral ramp is fixed to [ObsidianNeutrals.Dark]. A new accent needs an entry in both.
 */
@Composable
fun MessagesTheme(
    accent: AppAccent = AppAccent.Default,
    content: @Composable () -> Unit,
) {
    ObsidianTheme(accent = accent.toObsidian(), neutrals = ObsidianNeutrals.Dark, content = content)
}

fun AppAccent.toObsidian(): ObsidianAccent = when (this) {
    AppAccent.Crimson -> ObsidianAccent.Crimson
    AppAccent.Electric -> ObsidianAccent.Electric
    AppAccent.Solar -> ObsidianAccent.Solar
    AppAccent.Emerald -> ObsidianAccent.Emerald
    AppAccent.Luminous -> ObsidianAccent.Luminous
}

/** Reverse mapping, for Obsidian widgets that report an [ObsidianAccent] (e.g. the showcase swatches). */
fun ObsidianAccent.toAppAccent(): AppAccent =
    AppAccent.entries.firstOrNull { it.toObsidian() == this } ?: AppAccent.Default
