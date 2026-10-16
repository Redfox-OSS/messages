plugins {
    alias(libs.plugins.messages.android.library)
    alias(libs.plugins.messages.hilt)
}

android {
    namespace = "dev.redfox.messages.core.contacts"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
