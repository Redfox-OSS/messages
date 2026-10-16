plugins {
    alias(libs.plugins.messages.android.library)
    alias(libs.plugins.messages.hilt)
}

android {
    namespace = "dev.redfox.messages.core.common"
}

dependencies {
    api(libs.kotlinx.coroutines.android)
}
