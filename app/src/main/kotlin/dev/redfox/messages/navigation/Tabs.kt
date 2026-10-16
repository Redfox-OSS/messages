package dev.redfox.messages.navigation

import androidx.navigation3.runtime.NavKey
import dev.redfox.messages.core.ui.R
import dev.redfox.messages.feature.conversations.ConversationsKey
import dev.redfox.messages.feature.scheduled.ScheduledKey
import dev.redfox.messages.feature.settings.SettingsKey
import dev.redfox.messages.feature.starred.StarredKey
import dev.redfox.obsidian.navigation.FloatingNavItem
import dev.redfox.obsidian.navigation.NavIconMotion

/** The four top-level destinations, in nav-bar order (PLAN.md §14). */
internal data class Tab(val key: NavKey, val item: FloatingNavItem)

internal val Tabs = listOf(
    Tab(ConversationsKey, FloatingNavItem(R.drawable.ic_chat, "Messages", NavIconMotion.Jelly)),
    Tab(StarredKey, FloatingNavItem(R.drawable.ic_star, "Starred", NavIconMotion.Pop)),
    Tab(ScheduledKey, FloatingNavItem(R.drawable.ic_schedule, "Scheduled", NavIconMotion.Shake)),
    Tab(SettingsKey, FloatingNavItem(R.drawable.ic_settings, "Settings", NavIconMotion.Turn)),
)

internal val TabItems = Tabs.map { it.item }
