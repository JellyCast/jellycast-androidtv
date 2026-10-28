import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

// Without keystore.properties, debug falls back to the default debug key and release builds unsigned.
val keystoreProperties =
    Properties().apply {
        val file = rootProject.file("keystore.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
val hasReleaseKeystore = keystoreProperties.getProperty("storeFile") != null

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) file.inputStream().use(::load)
    }
val devServerUrl = localProperties.getProperty("jellyfin.serverUrl").orEmpty()

android {
    namespace = "nl.jellycast.tv"
    compileSdk = 37

    defaultConfig {
        applicationId = "nl.jellycast.tv"
        minSdk = 34
        targetSdk = 37
        versionCode = 60
        versionName = "0.1.59"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // The Chromecast is 32-bit; arm64 costs ~2 MB but keeps 64-bit-only devices working.
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a") }
    }

    signingConfigs {
        if (hasReleaseKeystore) {
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
            buildConfigField("String", "DEV_SERVER_URL", "\"$devServerUrl\"")
            buildConfigField("boolean", "DEV_TOOLS", "true")
            // Same key as release, so a release build installs over debug without uninstalling.
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
        release {
            buildConfigField("String", "DEV_SERVER_URL", "\"\"")
            buildConfigField("boolean", "DEV_TOOLS", "false")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
        // Release speed (R8, not debuggable) with logs and dev tools, for performance measurements.
        create("profile") {
            initWith(getByName("release"))
            buildConfigField("String", "DEV_SERVER_URL", "\"$devServerUrl\"")
            buildConfigField("boolean", "DEV_TOOLS", "true")
            matchingFallbacks += "release"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // The default language is in res/resources.properties.
    androidResources {
        generateLocaleConfig = true
    }
}

ktlint {
    version.set(libs.versions.ktlint)
    android.set(true)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.ui.tooling.preview)
    debugImplementation(libs.compose.ui.tooling)
    // Composable names in Perfetto traces, measurement build only.
    "profileImplementation"(libs.compose.runtime.tracing)
    "profileImplementation"(libs.tracing.perfetto.binary)

    implementation(libs.tv.material)

    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.viewmodel.navigation3)

    implementation(libs.navigation3.runtime)
    implementation(libs.navigation3.ui)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.media3.ui.compose)

    implementation(platform(libs.koin.bom))
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)

    implementation(libs.coil.compose)

    ktlintRuleset(libs.compose.rules.ktlint)

    testImplementation(project(":core-testing"))
    testImplementation(libs.junit)
    testImplementation(platform(libs.koin.bom))
    testImplementation(libs.koin.test)
    testImplementation(libs.koin.test.junit4)

    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    debugImplementation(libs.compose.ui.test.manifest)
}
