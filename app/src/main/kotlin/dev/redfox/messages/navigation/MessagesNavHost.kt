package dev.redfox.messages.navigation

import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import dev.redfox.messages.feature.conversations.ConversationsKey
import dev.redfox.messages.feature.conversations.ConversationsRoute
import dev.redfox.messages.feature.onboarding.OnboardingKey
import dev.redfox.messages.feature.onboarding.OnboardingRoute
import dev.redfox.messages.feature.settings.SettingsKey
import dev.redfox.messages.feature.settings.SettingsRoute
import dev.redfox.messages.feature.thread.ThreadKey
import dev.redfox.messages.feature.thread.ThreadRoute
import dev.redfox.messages.feature.thread.newchat.NewConversationKey
import dev.redfox.messages.feature.thread.newchat.NewConversationRoute
import dev.redfox.messages.saved.SavedKey
import dev.redfox.messages.saved.SavedRoute
import dev.redfox.obsidian.motion.ObMotion
import dev.redfox.obsidian.motion.RevealScope

@Composable
fun MessagesNavHost(
    navigator: Navigator,
    versionLabel: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    NavDisplay(
        backStack = navigator.backStack,
        modifier = modifier,
        onBack = { if (!navigator.back()) onExit() },
        entryDecorators = listOf(
            rememberSaveableStateHolderNavEntryDecorator(),
            rememberViewModelStoreNavEntryDecorator(),
        ),
        transitionSpec = { forwardTransition() },
        popTransitionSpec = { popTransition() },
        predictivePopTransitionSpec = { popTransition() },
        entryProvider = entryProvider {
            entry<OnboardingKey> { key ->
                Reveal(navigator, key) { OnboardingRoute(onFinished = { navigator.resetTo(ConversationsKey) }) }
            }
            entry<ConversationsKey> { key ->
                Reveal(navigator, key) {
                    ConversationsRoute(
                        onOpenThread = { navigator.navigate(ThreadKey(it)) },
                        onOpenSettings = { navigator.navigate(SettingsKey) },
                    )
                }
            }
            entry<ThreadKey> { key ->
                Reveal(navigator, key) { ThreadRoute(key = key, onBack = { navigator.back() }) }
            }
            entry<NewConversationKey> { key ->
                Reveal(navigator, key) {
                    NewConversationRoute(
                        onBack = { navigator.back() },
                        onOpenThread = { threadId, address, title ->
                            navigator.replaceCurrent(
                                ThreadKey(threadId, addresses = listOf(address), title = title, draft = key.draft)
                            )
                        },
                    )
                }
            }
            entry<SavedKey> { key ->
                Reveal(navigator, key) { SavedRoute(onOpenThread = { navigator.navigate(ThreadKey(it)) }) }
            }
            entry<SettingsKey> { key ->
                Reveal(navigator, key) {
                    SettingsRoute(
                        versionLabel = versionLabel,
                        onBack = { navigator.back() },
                    )
                }
            }
        },
    )
}

/** Every destination gets a fresh [RevealScope] — enabled on arrival, disabled when uncovered by back. */
@Composable
private fun Reveal(navigator: Navigator, key: NavKey, content: @Composable () -> Unit) {
    RevealScope(enabled = navigator.shouldReveal(key), content = content)
}

// Pushes drift in from the trailing edge on a gentle spring; pops dissolve back out.
private fun forwardTransition(): ContentTransform =
    (slideInHorizontally(ObMotion.gentle()) { it / 12 } + fadeIn(tween(220))) togetherWith
        fadeOut(tween(120))

private fun popTransition(): ContentTransform =
    fadeIn(tween(220)) togetherWith
        (slideOutHorizontally(ObMotion.gentle()) { it / 12 } + fadeOut(tween(160)))
