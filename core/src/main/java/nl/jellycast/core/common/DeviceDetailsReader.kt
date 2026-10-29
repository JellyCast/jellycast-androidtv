package nl.jellycast.core.common

import android.os.Build
import nl.jellycast.core.model.DeviceDetails

/** "Google Chromecast, Android 14": never `Settings.Global.DEVICE_NAME`, the owner's name for it. */
// The Build fields are null in JVM unit tests, which build the Koin modules too.
fun currentDeviceDetails(versionName: String, versionCode: Int): DeviceDetails = DeviceDetails(
    brand = Build.MANUFACTURER.orEmpty(),
    model = Build.MODEL.orEmpty(),
    os = "Android",
    osVersion = Build.VERSION.RELEASE.orEmpty(),
    appVersion = "$versionName ($versionCode)",
)
