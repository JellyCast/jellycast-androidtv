package nl.jellycast.tv.core.model

data class AppInfo(
    val versionName: String,
    val versionCode: Int,
    // Debug builds only (from local.properties); empty in release.
    val devServerUrl: String = "",
)
