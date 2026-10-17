package dev.redfox.messages

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.rememberHazeState
import dev.redfox.messages.core.model.AppAccent
import dev.redfox.messages.core.model.AppSettings
import dev.redfox.messages.core.telephony.MessageLaunchExtras
import dev.redfox.messages.core.ui.mvi.CollectEffects
import dev.redfox.messages.feature.thread.ThreadKey
import dev.redfox.messages.feature.thread.newchat.NewConversationKey
import kotlinx.coroutines.flow.Flow
import dev.redfox.messages.core.ui.theme.MessagesTheme
import dev.redfox.messages.core.ui.theme.toAppAccent
import dev.redfox.messages.feature.conversations.ConversationsKey
import dev.redfox.messages.feature.onboarding.OnboardingKey
import dev.redfox.messages.navigation.MessagesNavHost
import dev.redfox.messages.navigation.NewMessageItem
import dev.redfox.messages.navigation.SearchItem
import dev.redfox.messages.navigation.SearchKey
import dev.redfox.messages.navigation.TabItems
import dev.redfox.messages.navigation.Tabs
import dev.redfox.messages.navigation.rememberNavigator
import dev.redfox.obsidian.effects.LocalHazeState
import dev.redfox.obsidian.motion.ObMotion
import dev.redfox.messages.core.ui.navigation.CompactFloatingNavBar
import dev.redfox.messages.core.ui.navigation.rememberCompactNavState
import dev.redfox.obsidian.theme.ObsidianTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Obsidian is always dark: light system-bar icons regardless of the system theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        // Only a fresh launch: after a configuration change the stack already shows it.
        if (savedInstanceState == null) handleExternal(intent)
        setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            val settings = state.settings
            MessagesTheme(accent = settings?.accent ?: AppAccent.Default) {
                val hazeState = rememberHazeState()
                CompositionLocalProvider(LocalHazeState provides hazeState) {
                    Box(Modifier.fillMaxSize().background(ObsidianTheme.colors.background)) {
                        // Wait for the first settings read so the start destination is right.
                        if (settings != null) {
                            MessagesShell(
                                settings = settings,
                                effects = viewModel.effects,
                                onAccentChange = { viewModel.dispatch(MainIntent.AccentChanged(it)) },
                                onExit = ::finish,
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternal(intent)
    }

    private fun handleExternal(intent: Intent?) {
        intent?.toExternalOpen()?.let(viewModel::dispatch)
    }
}

/**
 * Reads where an incoming intent wants to go: a conversation from one of our notifications,
 * or another app's `sms:` / `smsto:` link or text share (`sms_body`, `EXTRA_TEXT`).
 */
private fun Intent.toExternalOpen(): MainIntent.OpenExternal? {
    val threadId = getLongExtra(MessageLaunchExtras.THREAD_ID, -1)
    val notificationAddress = getStringExtra(MessageLaunchExtras.ADDRESS)
    if (threadId > 0 || notificationAddress != null) {
        return MainIntent.OpenExternal(threadId, listOfNotNull(notificationAddress), body = null)
    }
    if (action != Intent.ACTION_SENDTO && action != Intent.ACTION_SEND && action != Intent.ACTION_VIEW) return null
    val uri = data
    val addresses = if (uri?.scheme in SmsSchemes) {
        Uri.decode(uri?.schemeSpecificPart.orEmpty()).substringBefore('?')
            .split(',', ';')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    } else emptyList()
    val body = getStringExtra("sms_body")
        ?: getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        ?: uri?.takeIf { it.isHierarchical }?.getQueryParameter("body")
        ?: Uri.decode(uri?.schemeSpecificPart.orEmpty()).substringAfter("?body=", "").ifEmpty { null }
    if (addresses.isEmpty() && body.isNullOrBlank()) return null
    return MainIntent.OpenExternal(threadId = -1, addresses = addresses, body = body)
}

private val SmsSchemes = setOf("sms", "smsto", "mms", "mmsto")

@Composable
private fun MessagesShell(
    settings: AppSettings,
    effects: Flow<MainEffect>,
    onAccentChange: (AppAccent) -> Unit,
    onExit: () -> Unit,
) {
    val navigator = rememberNavigator(if (settings.onboardingCompleted) ConversationsKey else OnboardingKey)
    val selectedTab = Tabs.indexOfFirst { it.key == navigator.current }
    // Compacts the nav while lists scroll toward their end; expands on reverse, top, or destination change.
    val (compactNav, navScroll) = rememberCompactNavState(resetKey = navigator.current)
    CollectEffects(effects) { effect ->
        // Before onboarding finishes there's no inbox to open into.
        if (!settings.onboardingCompleted) return@CollectEffects
        when (effect) {
            is MainEffect.OpenThread -> navigator.openFromOutside(
                ThreadKey(effect.threadId, addresses = effect.addresses, draft = effect.draft)
            )
            is MainEffect.PickRecipient -> navigator.openFromOutside(NewConversationKey(effect.draft))
        }
    }

    Box(Modifier.fillMaxSize().nestedScroll(navScroll)) {
        MessagesNavHost(
            navigator = navigator,
            versionLabel = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            onShowcaseAccentChange = { onAccentChange(it.toAppAccent()) },
            onExit = onExit,
        )
        // The floating nav belongs to tab destinations only; pushed screens get the full height.
        AnimatedVisibility(
            visible = selectedTab >= 0,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(ObMotion.gentle()) { it } + fadeIn(),
            exit = slideOutVertically(ObMotion.gentle()) { it } + fadeOut(),
        ) {
            CompactFloatingNavBar(
                items = TabItems,
                selectedIndex = selectedTab,
                onSelect = { navigator.selectTab(Tabs[it].key) },
                compact = compactNav,
                action = NewMessageItem,
                onAction = { navigator.navigate(NewConversationKey()) },
                pill = SearchItem,
                onPill = { navigator.navigate(SearchKey) },
            )
        }
    }
}
