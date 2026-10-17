plugins {
    alias(libs.plugins.messages.android.feature)
}

android {
    namespace = "dev.redfox.messages.feature.search"
}

dependencies {
    // BackHandler, to close search with back.
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.paging.compose)
}

dependencies {
    testImplementation(libs.junit)
}
