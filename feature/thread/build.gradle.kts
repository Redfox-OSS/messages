plugins {
    alias(libs.plugins.messages.android.feature)
}

android {
    namespace = "dev.redfox.messages.feature.thread"
}

dependencies {
    implementation(libs.androidx.paging.compose)
    // Photo picker and microphone permission.
    implementation(libs.androidx.activity.compose)
}
