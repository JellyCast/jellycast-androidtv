plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Shared by the TV and the mobile app: models, Jellyfin access, repositories and the playback
// engine. No UI.
android {
    namespace = "nl.jellycast.core"
    compileSdk = 37

    defaultConfig {
        minSdk = 34
        consumerProguardFiles("consumer-rules.pro")
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

dependencies {
    implementation(libs.androidx.core.ktx)

    api(libs.kotlinx.coroutines.android)
    api(libs.kotlinx.serialization.core)
    api(libs.kotlinx.serialization.json)

    api(libs.jellyfin.core)
    api(libs.datastore.preferences)

    api(libs.media3.exoplayer)
    api(libs.media3.exoplayer.hls)
    api(libs.media3.ffmpeg.decoder)

    api(libs.coil)
    api(libs.coil.network.okhttp)

    api(libs.timber)
    // The Jellyfin SDK logs via SLF4J and crashes on Android without an implementation.
    implementation(libs.slf4j.timber)

    testImplementation(project(":core-testing"))
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
