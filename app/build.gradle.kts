import java.util.Properties

plugins {
    alias(libs.plugins.messages.android.application)
    alias(libs.plugins.messages.android.compose)
    alias(libs.plugins.messages.hilt)
    alias(libs.plugins.kotlin.serialization)
}

// Sideload signing key, read from the git-ignored keystore.properties in the project root.
val keystoreProperties = Properties().apply {
    val file = rootProject.file("keystore.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val hasReleaseKey = keystoreProperties.getProperty("storeFile") != null

android {
    namespace = "dev.redfox.messages"

    defaultConfig {
        applicationId = "dev.redfox.messages"
        versionCode = 2
        versionName = "0.1.1"
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Both target phones (S23, OnePlus 13) are 64-bit ARM; skip the other CPUs' native libs.
            ndk { abiFilters += "arm64-v8a" }
            // The release key when keystore.properties exists; otherwise the debug key, so a
            // release APK can still be built and sideloaded.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    // The app is English-only; drop the 80+ translations bundled by AndroidX libraries.
    androidResources {
        localeFilters += "en"
    }
    packaging {
        resources {
            excludes += listOf(
                // Library licence texts and version markers: not used at runtime.
                "META-INF/androidx/**",
                "META-INF/*.version",
                "META-INF/**/LICENSE*",
                "META-INF/**/NOTICE*",
                // Only kotlin-reflect reads these; the app doesn't use reflection.
                "kotlin/**.kotlin_builtins",
                // kotlinx-coroutines debug-agent probes.
                "DebugProbesKt.bin",
            )
        }
    }
}

dependencies {
    // The debug-only search benchmark reads the first page of results.
    debugImplementation(libs.androidx.paging.testing)
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:datastore"))
    implementation(project(":core:telephony"))
    implementation(project(":core:contacts"))
    implementation(project(":core:database"))
    implementation(project(":core:ui"))

    implementation(project(":feature:onboarding"))
    implementation(project(":feature:conversations"))
    implementation(project(":feature:thread"))
    implementation(project(":feature:starred"))
    implementation(project(":feature:scheduled"))
    implementation(project(":feature:settings"))
    implementation(project(":feature:search"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.kotlinx.serialization.core)
}
