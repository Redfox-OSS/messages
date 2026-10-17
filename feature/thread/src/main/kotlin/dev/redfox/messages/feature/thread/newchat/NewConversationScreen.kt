package dev.redfox.messages.feature.thread.newchat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dev.redfox.messages.core.model.Contact
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.core.ui.components.Avatar
import dev.redfox.messages.core.ui.components.DetailPage
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.obsidian.components.EmptyState
import dev.redfox.obsidian.components.ObListRow
import dev.redfox.obsidian.effects.glassSource
import dev.redfox.obsidian.effects.pressable
import dev.redfox.obsidian.motion.revealOnEnter
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme
import dev.redfox.obsidian.theme.ScreenGutter
import kotlinx.serialization.Serializable

/** The recipient picker; [draft] is text shared from another app, carried into the chosen thread. */
@Serializable
data class NewConversationKey(val draft: String? = null) : NavKey

@Composable
fun NewConversationRoute(
    onBack: () -> Unit,
    onOpenThread: (threadId: Long, address: String, title: String) -> Unit,
    viewModel: NewConversationViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    CollectEffects(viewModel.effects) { effect ->
        when (effect) {
            is NewConversationEffect.OpenThread -> onOpenThread(effect.threadId, effect.address, effect.title)
        }
    }
    NewConversationScreen(state = state, onBack = onBack, onIntent = viewModel::dispatch)
}

@Composable
fun NewConversationScreen(
    state: NewConversationState,
    onBack: () -> Unit,
    onIntent: (NewConversationIntent) -> Unit,
) {
    DetailPage(title = "New conversation", onBack = onBack) { padding ->
        Box(Modifier.fillMaxSize().imePadding()) {
            LazyColumn(
                modifier = Modifier.glassSource().fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding()),
            ) {
                item(key = "field") {
                    RecipientField(
                        query = state.query,
                        onQueryChange = { onIntent(NewConversationIntent.QueryChanged(it)) },
                        onDone = { state.typedNumber?.let { onIntent(NewConversationIntent.Pick(it, it)) } },
                    )
                }
                state.typedNumber?.let { number ->
                    item(key = "typed") {
                        ObListRow(
                            title = "Send to $number",
                            icon = R.drawable.ic_dialpad,
                            iconTint = ObsidianTheme.colors.accentLight,
                            onClick = { onIntent(NewConversationIntent.Pick(number, number)) },
                            modifier = Modifier.padding(horizontal = ScreenGutter - ObSpacing.S),
                        )
                    }
                }
                items(state.results, key = { "c:" + it.address + it.name }) { contact ->
                    ContactRow(
                        contact = contact,
                        onClick = { onIntent(NewConversationIntent.Pick(contact.address, contact.displayName)) },
                        modifier = Modifier.revealOnEnter(minOf(state.results.indexOf(contact), 10)),
                    )
                }
                if (state.results.isEmpty() && state.typedNumber == null) {
                    item(key = "empty") {
                        EmptyState(
                            icon = R.drawable.ic_contacts,
                            title = if (state.query.isBlank()) "No contacts" else "No matches",
                            subtitle = "Type a phone number to message it directly",
                            modifier = Modifier.padding(top = ObSpacing.XXL),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecipientField(query: String, onQueryChange: (String) -> Unit, onDone: () -> Unit) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = ScreenGutter, vertical = ObSpacing.S)
            .background(colors.tile, CircleShape)
            .border(1.dp, colors.border, CircleShape)
            .padding(horizontal = ObSpacing.L, vertical = ObSpacing.M),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.M),
    ) {
        Text("To", style = type.labelStrong, color = colors.textSecondary)
        Box(Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Name or number", style = type.body, color = colors.textTertiary)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = type.body.copy(color = colors.textPrimary),
                cursorBrush = SolidColor(colors.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { onDone() }),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                painterResource(R.drawable.ic_close),
                contentDescription = "Clear",
                tint = colors.textSecondary,
                modifier = Modifier.size(20.dp).pressable { onQueryChange("") },
            )
        }
    }
}

@Composable
private fun ContactRow(contact: Contact, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = ObsidianTheme.colors
    val type = ObsidianTheme.typography
    Row(
        modifier
            .fillMaxWidth()
            .pressable(onClick = onClick)
            .padding(horizontal = ScreenGutter, vertical = ObSpacing.S),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(ObSpacing.M),
    ) {
        Avatar(listOf(contact), size = 44.dp)
        Column(Modifier.weight(1f)) {
            Text(contact.displayName, style = type.bodyLarge, color = colors.textPrimary, maxLines = 1)
            if (contact.name != null) {
                Text(contact.formattedAddress, style = type.label, color = colors.textSecondary, maxLines = 1)
            }
        }
    }
}
