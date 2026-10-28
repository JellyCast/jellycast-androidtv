plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ktlint)
}

// Fakes and test data for the JVM tests of :core and both apps.
android {
    namespace = "nl.jellycast.core.testing"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

dependencies {
    api(project(":core"))
    api(libs.junit)
    api(libs.kotlinx.coroutines.test)
}
