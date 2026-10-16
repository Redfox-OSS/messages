plugins {
    alias(libs.plugins.messages.jvm.library)
}

dependencies {
    api(project(":core:model"))
    api(libs.kotlinx.coroutines.core)
    // Pure-Kotlin paging types, so repositories can expose PagingData without Android.
    api(libs.androidx.paging.common)
    implementation(libs.javax.inject)
    implementation(libs.kotlinx.serialization.json)
}
