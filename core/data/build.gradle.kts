plugins {
    alias(libs.plugins.messages.android.library)
    alias(libs.plugins.messages.hilt)
}

android {
    namespace = "dev.redfox.messages.core.data"
}

dependencies {
    api(project(":core:domain"))
    implementation(project(":core:common"))
    implementation(project(":core:datastore"))
    implementation(project(":core:telephony"))
    implementation(project(":core:contacts"))
    implementation(project(":core:database"))
}
