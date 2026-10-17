plugins {
    alias(libs.plugins.messages.android.library)
}

android {
    namespace = "dev.redfox.messages.core.mms"
}

// AOSP's MMS PDU parser/composer (see NOTICE), kept as-is in Java.

android {
    // The PDU code logs through android.util.Log; return defaults instead of throwing in JVM tests.
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    testImplementation(libs.junit)
}
