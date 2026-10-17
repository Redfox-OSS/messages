package dev.redfox.messages.navigation

import androidx.navigation3.runtime.NavKey
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.feature.conversations.ConversationsKey
import dev.redfox.messages.saved.SavedKey
import dev.redfox.obsidian.navigation.FloatingNavItem
import dev.redfox.obsidian.navigation.NavIconMotion

/** A top-level destination in the nav bar. */
internal data class Tab(val key: NavKey, val item: FloatingNavItem)

/** The bar's tabs, in order: messages first, then everything kept for later. */
internal val Tabs = listOf(
    Tab(ConversationsKey, FloatingNavItem(R.drawable.ic_chat, "Messages", NavIconMotion.Jelly)),
    Tab(SavedKey, FloatingNavItem(R.drawable.ic_bookmark, "Saved", NavIconMotion.Pop)),
)

internal val TabItems = Tabs.map { it.item }

/** The bar's last slot: a plain action, not a tab (new message is rare in India; it stays modest). */
internal val NewMessageItem = FloatingNavItem(R.drawable.ic_edit_square, "New message", NavIconMotion.Press)

/** The separate accented pill beside the bar. */
internal val SearchItem = FloatingNavItem(R.drawable.ic_search, "Search", NavIconMotion.Pop)
