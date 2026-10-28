# Third-party notices

The source code of JellyCast is [MIT](LICENSE). The app built from it includes the libraries below, each under its own license. Versions are the ones in `gradle/libs.versions.toml`; [docs/dependencies.md](docs/dependencies.md) says why each one is there.

## The APK as a whole: GPL-3.0

The FFmpeg audio decoder is Jellyfin's build for Media3, licensed under **GPL-3.0**. An APK that includes it is therefore distributed under GPL-3.0 as a whole: whoever shares the APK must also offer its complete source code under GPL-3.0-compatible terms. The MIT source in this repository meets that, as long as it is shared with the APK.

A build without `media3-ffmpeg-decoder` leaves only the MIT, Apache-2.0 and LGPL-3.0 parts below; it then can't play the audio formats the TV can't decode itself.

## Libraries

| Library | Version | License | Source |
|---|---|---|---|
| Jellyfin Media3 FFmpeg decoder | 1.9.0+1 | GPL-3.0 | https://github.com/jellyfin/jellyfin-androidx-media |
| Jellyfin Kotlin SDK | 1.8.12 | LGPL-3.0 | https://github.com/jellyfin/jellyfin-sdk-kotlin |
| AndroidX Media3 (ExoPlayer, HLS, session, UI) | 1.11.1 | Apache-2.0 | https://github.com/androidx/media |
| Jetpack Compose (BOM) | 2026.09.00 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Compose for TV (tv-material) | 1.1.0 | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| AndroidX Activity, Lifecycle, Navigation 3, DataStore | see `libs.versions.toml` | Apache-2.0 | https://developer.android.com/jetpack/androidx |
| Kotlin standard library, kotlinx.coroutines, kotlinx.serialization | see `libs.versions.toml` | Apache-2.0 | https://github.com/JetBrains/kotlin |
| Koin | 4.2.2 | Apache-2.0 | https://github.com/InsertKoinIO/koin |
| Coil 3 (with OkHttp) | 3.6.3 | Apache-2.0 | https://github.com/coil-kt/coil |
| Timber | 5.0.1 | Apache-2.0 | https://github.com/JakeWharton/timber |
| slf4j-timber | 0.0.8 | Apache-2.0 | https://github.com/unveloper/slf4j-timber |
| SLF4J API (via the Jellyfin SDK) | | MIT | https://github.com/qos-ch/slf4j |

These libraries bring their own dependencies, each under its own license; `./gradlew :tv:dependencies --configuration releaseRuntimeClasspath` lists them all.

## Trademarks

Jellyfin is a trademark of the Jellyfin project; JellyCast is not an official Jellyfin project. Android TV, Google TV and Chromecast are trademarks of Google LLC.
