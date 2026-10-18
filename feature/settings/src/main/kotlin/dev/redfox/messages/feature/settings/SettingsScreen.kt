package dev.redfox.messages.feature.settings

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.redfox.messages.core.model.SwipeAction
import dev.redfox.messages.core.model.VibrationPattern
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.theme.toAppAccent
import dev.redfox.messages.core.ui.theme.toObsidian
import dev.redfox.obsidian.components.AccentSwatchRow
import dev.redfox.obsidian.components.DetailPage
import dev.redfox.obsidian.components.ObCard
import dev.redfox.obsidian.components.ObDivider
import dev.redfox.obsidian.components.ObDropdownMenu
import dev.redfox.obsidian.components.ObListRow
import dev.redfox.obsidian.components.ObMenuItem
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
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.dispatch(SettingsIntent.Refresh) }
    SettingsScreen(state = state, versionLabel = versionLabel, onBack = onBack, onIntent = viewModel::dispatch)
}

@Composable
fun SettingsScreen(
    state: SettingsState,
    versionLabel: String,
    onBack: () -> Unit,
    onIntent: (SettingsIntent) -> Unit,
) {
    DetailPage(title = "Settings", onBack = onBack) { padding ->
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
                    selected = state.accent.toObsidian(),
                    onSelect = { onIntent(SettingsIntent.AccentSelected(it.toAppAccent())) },
                    modifier = Modifier.padding(top = ObSpacing.L, start = ObSpacing.XXS)
                )
            }
            Section("Conversations") {
                ChoiceRow(
                    title = "Swipe left",
                    icon = R.drawable.ic_swipe_left,
                    selected = state.swipeLeft,
                    options = SwipeAction.entries,
                    label = { it.label },
                    onSelect = { onIntent(SettingsIntent.SwipeActionSelected(left = true, action = it)) },
                )
                ObDivider(Modifier.padding(vertical = ObSpacing.M))
                ChoiceRow(
                    title = "Swipe right",
                    icon = R.drawable.ic_swipe_right,
                    selected = state.swipeRight,
                    options = SwipeAction.entries,
                    label = { it.label },
                    onSelect = { onIntent(SettingsIntent.SwipeActionSelected(left = false, action = it)) },
                )
            }
            Section("Notifications") {
                ChoiceRow(
                    title = "Vibration",
                    icon = R.drawable.ic_notifications,
                    selected = state.vibration,
                    options = VibrationPattern.entries,
                    label = { it.label },
                    onSelect = { onIntent(SettingsIntent.VibrationSelected(it)) },
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

/** A settings row whose value is picked from a dropdown. */
@Composable
private fun <T> ChoiceRow(
    title: String,
    @DrawableRes icon: Int,
    selected: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        ObListRow(
            title = title,
            icon = icon,
            subtitle = label(selected),
            onClick = { open = true },
            trailing = { Chevron() },
        )
        ObDropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                ObMenuItem(
                    text = label(option),
                    selected = option == selected,
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
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

