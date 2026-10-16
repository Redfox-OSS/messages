plugins {
    alias(libs.plugins.messages.android.library)
    alias(libs.plugins.messages.hilt)
}

android {
    namespace = "dev.redfox.messages.core.datastore"
}

dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.datastore.preferences)
}
