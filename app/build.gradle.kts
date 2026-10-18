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
        // Static literals: F-Droid reads them from this file to detect new releases.
        versionCode = 4
        versionName = "1.0.0"
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
            // Signed with the release key when keystore.properties exists; otherwise left unsigned,
            // which is what F-Droid's build server expects (it then checks the result against the
            // signed APK on the GitHub release; see fdroid/README.md).
            if (hasReleaseKey) signingConfig = signingConfigs.getByName("release")
        }
    }
    buildFeatures {
        buildConfig = true
    }
    // The app is English-only; drop the 80+ translations bundled by AndroidX libraries.
    androidResources {
        localeFilters += "en"
    }
    // No Google-encrypted dependency report in the APK: F-Droid can't read it, and the source is public.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
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
