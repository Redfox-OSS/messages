package dev.redfox.messages.core.model

/** The accent the user picked. Maps 1:1 onto the Obsidian accent presets in `:core:ui`. */
enum class AppAccent {
    Crimson,
    Electric,
    Solar,
    Emerald,
    Luminous;

    companion object {
        val Default = Electric
    }
}

/** What swiping an inbox conversation does (Gmail-style, set per direction in Settings). */
enum class SwipeAction(val label: String) {
    None("None"),
    Archive("Archive"),
    Delete("Delete"),
    ToggleRead("Mark read / unread"),
    Pin("Pin / unpin"),
    Call("Call"),
}

/** Vibration for new-message notifications. */
enum class VibrationPattern(val label: String, val timings: LongArray?) {
    Off("Off", null),
    Default("Default", longArrayOf(0, 250, 120, 250)),
    Short("Short", longArrayOf(0, 120)),
    Long("Long", longArrayOf(0, 700)),
    Heartbeat("Heartbeat", longArrayOf(0, 90, 110, 90, 600, 90, 110, 90)),
}

data class AppSettings(
    val accent: AppAccent = AppAccent.Default,
    val onboardingCompleted: Boolean = false,
    /** Swipe a conversation from right to left. */
    val swipeLeft: SwipeAction = SwipeAction.Archive,
    /** Swipe a conversation from left to right. */
    val swipeRight: SwipeAction = SwipeAction.ToggleRead,
    val vibration: VibrationPattern = VibrationPattern.Default,
)
