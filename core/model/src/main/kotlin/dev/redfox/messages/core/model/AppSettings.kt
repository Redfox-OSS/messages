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

data class AppSettings(
    val accent: AppAccent = AppAccent.Default,
    val onboardingCompleted: Boolean = false,
)
