plugins {
    alias(libs.plugins.messages.android.library)
    alias(libs.plugins.messages.hilt)
}

android {
    namespace = "dev.redfox.messages.core.telephony"
}

dependencies {
    implementation(project(":core:common"))
    implementation(project(":core:domain"))
    api(project(":core:mms"))
    implementation(libs.androidx.core.ktx)
}
