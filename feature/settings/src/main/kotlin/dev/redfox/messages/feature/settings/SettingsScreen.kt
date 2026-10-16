package dev.redfox.messages.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.AccentSwatchRow
import dev.redfox.messages.core.ui.components.TabPage
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.ObListRow
import dev.redfox.obsidian.components.SectionLabel
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.motion.RevealColumn
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.serialization.Serializable

@Serializable
data object SettingsKey : NavKey

@Composable
fun SettingsRoute(
    versionLabel: String,
    onOpenDesignShowcase: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(SettingsIntent.Refresh) }
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            SettingsEffect.OpenDesignShowcase -> onOpenDesignShowcase()
        }
    }
    SettingsScreen(state = state, versionLabel = versionLabel, onIntent = viewModel::dispatch)
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    versionLabel: String,
    onIntent: (SettingsIntent) -> Unit,
) {
    TabPage(title = "Settings") { padding ->
        RevealColumn(
            modifier = Modifier
                .glassSource()
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = ScreenGutter),
            verticalArrangement = Arrangement.spacedBy(ObSpacing.XL)
        ) {
            Section("Appearance") {
                ObListRow(
                    title = "Accent",
                    icon = R.drawable.ic_palette,
                    subtitle = state.accent.name,
                )
                AccentSwatchRow(
                    selected = state.accent,
                    onSelect = { onIntent(SettingsIntent.AccentSelected(it)) },
                    modifier = Modifier.padding(top = ObSpacing.L, start = ObSpacing.XXS)
                )
            }
            Section("Setup") {
                ObListRow(
                    title = "Default SMS app",
                    icon = R.drawable.ic_sms,
                    subtitle = if (state.isDefaultSmsApp) "Messages is the default" else "Another app receives texts",
                )
            }
            Section("Developer") {
                ObListRow(
                    title = "Design showcase",
                    icon = R.drawable.ic_bug_report,
                    subtitle = "Obsidian tokens & components",
                    onClick = { onIntent(SettingsIntent.DesignShowcaseClicked) },
                    trailing = { Chevron() },
                )
                ObDivider(Modifier.padding(vertical = ObSpacing.M))
                ObListRow(
                    title = "Version",
                    icon = R.drawable.ic_check_circle,
                    subtitle = versionLabel,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ObSpacing.M)) {
        SectionLabel(title)
        ObCard(Modifier.fillMaxWidth()) { content() }
    }
}

@Composable
private fun Chevron() {
    Icon(
        painterResource(R.drawable.ic_chevron_right),
        contentDescription = null,
        tint = ObsidianTheme.colors.textTertiary,
    )
}

