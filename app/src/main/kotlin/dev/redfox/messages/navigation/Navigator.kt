package dev.redfox.messages.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberNavBackStack
import dev.redfox.messages.feature.conversations.ConversationsKey

/**
 * Owns the back stack and the reveal policy: a destination plays its Obsidian entrance when it
 * is navigated *to*, but appears in place when uncovered by going back.
 *
 * Tabs keep a flat stack: Messages is always the root, any other tab sits on top of it, so back
 * from a tab returns to the inbox (as in Google Messages) and back from the inbox exits.
 */
@Stable
class Navigator(val backStack: NavBackStack<NavKey>) {

    private val uncovered = mutableStateMapOf<NavKey, Boolean>()

    val current: NavKey? get() = backStack.lastOrNull()

    fun shouldReveal(key: NavKey): Boolean = uncovered[key] != true

    fun navigate(key: NavKey) {
        uncovered.remove(key)
        backStack.add(key)
    }

    fun selectTab(key: NavKey) {
        if (current == key) return
        uncovered.remove(key)
        backStack.clear()
        backStack.add(ConversationsKey)
        if (key != ConversationsKey) backStack.add(key)
    }

    /** Replaces the whole stack, e.g. when onboarding finishes. */
    fun resetTo(key: NavKey) {
        uncovered.remove(key)
        backStack.clear()
        backStack.add(key)
    }

    /** Returns false when there is nothing to pop (the caller should finish the activity). */
    fun back(): Boolean {
        if (backStack.size <= 1) return false
        backStack.removeAt(backStack.lastIndex)
        current?.let { uncovered[it] = true }
        return true
    }
}

@Composable
fun rememberNavigator(start: NavKey): Navigator {
    val backStack = rememberNavBackStack(start)
    return remember(backStack) { Navigator(backStack) }
}
