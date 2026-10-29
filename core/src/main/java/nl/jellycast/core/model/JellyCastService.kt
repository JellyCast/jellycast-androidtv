package nl.jellycast.core.model

/** One ad from the JellyCast service, chosen by country only. */
data class AdCampaign(
    val id: Int,
    val position: Int?,
    val imageUrl: String,
    val title: String?,
    val advertiser: String,
    val url: String?,
    val seconds: Int?,
    val untilMs: Long,
)

/** What the service wants on each ad placement right now; `null` is nothing. */
data class AdPlacements(val country: String?, val home: AdCampaign?, val splash: AdCampaign?)

enum class UsageChoice { Allowed, Declined }

/** The kind of device and the app version, for support. Never the device's own name. */
data class DeviceDetails(
    val brand: String,
    val model: String,
    val os: String,
    val osVersion: String,
    val appVersion: String,
)

/** Who this app is to the service: `android` or `androidtv`, and the device it runs on. */
data class ServiceClientInfo(
    val platform: String,
    val device: DeviceDetails,
    val addresses: List<String> = TEST_SERVICE_ADDRESSES,
)

/**
 * The test server on the home LAN, then the same machine over the VPN, until the service has a
 * public address.
 */
val TEST_SERVICE_ADDRESSES = listOf("http://192.168.1.4:8090", "http://10.8.0.1:8090")
