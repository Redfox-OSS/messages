plugins {
    alias(libs.plugins.messages.android.feature)
}

android {
    namespace = "dev.redfox.messages.feature.onboarding"
}

dependencies {
    // ActivityResult launchers for the SMS role, runtime permissions and battery exemption.
    implementation(libs.androidx.activity.compose)
}
