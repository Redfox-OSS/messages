package dev.redfox.messages

import android.graphics.Color
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
import dev.redfox.messages.core.ui.theme.MessagesTheme
import dev.redfox.messages.core.ui.theme.toAppAccent
import dev.redfox.messages.feature.conversations.ConversationsKey
import dev.redfox.messages.feature.onboarding.OnboardingKey
import dev.redfox.messages.navigation.MessagesNavHost
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
                                onAccentChange = { viewModel.dispatch(MainIntent.AccentChanged(it)) },
                                onExit = ::finish,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessagesShell(
    settings: AppSettings,
    onAccentChange: (AppAccent) -> Unit,
    onExit: () -> Unit,
) {
    val navigator = rememberNavigator(if (settings.onboardingCompleted) ConversationsKey else OnboardingKey)
    val selectedTab = Tabs.indexOfFirst { it.key == navigator.current }
    // Compacts the nav while lists scroll toward their end; expands on reverse, top, or destination change.
    val (compactNav, navScroll) = rememberCompactNavState(resetKey = navigator.current)

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
            )
        }
    }
}
