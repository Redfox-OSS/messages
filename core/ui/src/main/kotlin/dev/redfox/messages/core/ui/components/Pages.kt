package dev.redfox.messages.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.redfox.obsidian.components.DetailHeader
import dev.redfox.obsidian.components.DetailHeaderHeight
import dev.redfox.obsidian.components.LargeHeader
import dev.redfox.obsidian.components.LargeHeaderHeight
import dev.redfox.obsidian.navigation.floatingNavClearance
import dev.redfox.obsidian.theme.ObSpacing
import dev.redfox.obsidian.theme.ObsidianTheme

/**
 * Layout for a tab destination: a frosted [LargeHeader] floating over the page's content, with
 * padding that clears the header and the floating nav bar. [content] must mark its scrolling
 * container with `Modifier.glassSource()` so the header can blur it.
 */
@Composable
fun TabPage(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + LargeHeaderHeight
    val bottom = floatingNavClearance()
    Box(modifier.fillMaxSize().background(ObsidianTheme.colors.background)) {
        content(PaddingValues(top = top + ObSpacing.S, bottom = bottom + ObSpacing.L))
        LargeHeader(title = title, actions = actions)
    }
}

/** Layout for a pushed destination: [DetailHeader] with back, no nav bar. */
@Composable
fun DetailPage(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (contentPadding: PaddingValues) -> Unit,
) {
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + DetailHeaderHeight
    val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    Box(modifier.fillMaxSize().background(ObsidianTheme.colors.background)) {
        content(PaddingValues(top = top + ObSpacing.S, bottom = bottom + ObSpacing.L))
        DetailHeader(title = title, onBack = onBack, actions = actions)
    }
}
