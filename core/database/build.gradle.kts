plugins {
    alias(libs.plugins.messages.android.library)
    alias(libs.plugins.messages.hilt)
    alias(libs.plugins.room)
}

android {
    namespace = "dev.redfox.messages.core.database"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(libs.androidx.room.runtime)
    api(libs.androidx.room.ktx)
    api(libs.androidx.room.paging)
    ksp(libs.androidx.room.compiler)
}
