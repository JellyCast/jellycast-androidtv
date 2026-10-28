package nl.jellycast.mobile.core.settings

import nl.jellycast.mobile.core.network.NetworkKind

/** A server transcode level, or the original file. [raw] is what is stored. */
enum class QualityLevel(
    val raw: Int,
    val maxWidth: Int?,
    val maxBitrate: Long?,
    val shortName: String,
) {
    P720(0, 1280, 2_500_000, "720p"),
    P1080(1, 1920, 5_000_000, "1080p"),
    P1440(2, 2560, 10_000_000, "1440p"),
    Original(3, null, null, "Original"),
    ;

    /** A level that would not shrink the file plays the original. */
    fun resolvedFor(sourceBitrate: Long?, sourceWidth: Int?): QualityLevel {
        if (this == Original) return Original
        val bitrateFits = sourceBitrate != null && maxBitrate != null && sourceBitrate <= maxBitrate
        val widthFits = sourceWidth != null && maxWidth != null && sourceWidth <= maxWidth
        return if (bitrateFits && widthFits) Original else this
    }

    companion object {
        fun fromRaw(raw: Int): QualityLevel? = entries.firstOrNull { it.raw == raw }

        /** The levels that really shrink this file, highest first; Original is always there. */
        fun offered(sourceBitrate: Long?, sourceWidth: Int?): List<QualityLevel> =
            entries.reversed().filter { it.resolvedFor(sourceBitrate, sourceWidth) == it }
    }
}

/** Auto picks once at the start, up to [Auto.upTo]; Fixed always plays [Fixed.level]. */
sealed interface QualityPreference {
    data class Auto(val upTo: QualityLevel = QualityLevel.Original) : QualityPreference

    data class Fixed(val level: QualityLevel) : QualityPreference

    val stored: String
        get() = when (this) {
            is Auto -> "auto-${upTo.raw}"
            is Fixed -> "fixed-${level.raw}"
        }

    companion object {
        /** In the order of the settings picker. */
        val all: List<QualityPreference> =
            listOf(
                Auto(),
                Auto(QualityLevel.P1440),
                Auto(QualityLevel.P1080),
                Auto(QualityLevel.P720),
                Fixed(QualityLevel.Original),
                Fixed(QualityLevel.P1440),
                Fixed(QualityLevel.P1080),
                Fixed(QualityLevel.P720),
            )

        fun parse(stored: String?): QualityPreference? {
            val (kind, raw) = stored?.split("-")?.takeIf { it.size == 2 } ?: return null
            val level = raw.toIntOrNull()?.let(QualityLevel::fromRaw) ?: return null
            return when (kind) {
                "auto" -> Auto(level)
                "fixed" -> Fixed(level)
                else -> null
            }
        }

        fun defaultFor(network: NetworkKind): QualityPreference = when (network) {
            NetworkKind.Local -> Fixed(QualityLevel.Original)
            NetworkKind.Wifi, NetworkKind.None -> Auto()
            NetworkKind.Cellular -> Auto(QualityLevel.P1080)
        }
    }
}

/**
 * Auto: the highest level up to the ceiling whose bitrate × 1.3 fits the measured connection
 * (Original counts with the file's own bitrate); without a measurement the ceiling; 720p when
 * nothing fits.
 */
fun QualityPreference.pick(measuredBitsPerSecond: Long?, sourceBitrate: Long?): QualityLevel =
    when (this) {
        is QualityPreference.Fixed -> level

        is QualityPreference.Auto ->
            if (measuredBitsPerSecond == null) {
                upTo
            } else {
                QualityLevel.entries
                    .filter { it.raw <= upTo.raw }
                    .reversed()
                    .firstOrNull { level ->
                        val bitrate = level.maxBitrate ?: sourceBitrate
                        bitrate != null && bitrate * AUTO_HEADROOM <= measuredBitsPerSecond
                    } ?: QualityLevel.P720
            }
    }

private const val AUTO_HEADROOM = 1.3
