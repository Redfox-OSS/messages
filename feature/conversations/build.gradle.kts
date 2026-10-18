plugins {
    alias(libs.plugins.messages.android.feature)
}

android {
    namespace = "dev.redfox.messages.feature.conversations"
}

dependencies {
    implementation(libs.androidx.paging.compose)
    implementation(libs.androidx.activity.compose)

    testImplementation(libs.junit)
}
