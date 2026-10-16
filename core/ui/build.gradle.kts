plugins {
    alias(libs.plugins.messages.android.library)
    alias(libs.plugins.messages.android.compose)
}

android {
    namespace = "dev.redfox.messages.core.ui"
}

dependencies {
    // Obsidian brings Compose UI, Material 3 and Haze as `api`; re-exported so features see one design system.
    api(libs.obsidian)
    api(libs.androidx.lifecycle.viewmodel.compose)
    api(libs.androidx.lifecycle.runtime.compose)
    api(libs.kotlinx.collections.immutable)
    api(project(":core:model"))
    implementation(libs.coil.compose)
    // Chatbot card images are remote (https); loaded only when the user opted into link previews.
    implementation(libs.coil.network.okhttp)
}
