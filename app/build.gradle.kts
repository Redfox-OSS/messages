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
        versionCode = 1
        versionName = "0.1.0-m0"
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
            // The release key when keystore.properties exists; otherwise the debug key, so a
            // release APK can still be built and sideloaded.
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "release" else "debug")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
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
