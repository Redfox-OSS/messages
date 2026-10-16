package dev.redfox.messages.core.model

/** What the OS currently grants the app. Read fresh whenever the app resumes. */
data class DeviceSetupStatus(
    val isDefaultSmsApp: Boolean,
    val hasContactsPermission: Boolean,
    val hasNotificationPermission: Boolean,
    val isIgnoringBatteryOptimizations: Boolean,
    /** Granted with the SMS role, or on its own for a sideloaded app that isn't the default yet. */
    val hasReadSmsPermission: Boolean = false,
) {
    val hasRuntimePermissions: Boolean get() = hasContactsPermission && hasNotificationPermission

    companion object {
        val Unknown = DeviceSetupStatus(
            isDefaultSmsApp = false,
            hasContactsPermission = false,
            hasNotificationPermission = false,
            isIgnoringBatteryOptimizations = false,
            hasReadSmsPermission = false,
        )
    }
}
