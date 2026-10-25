package nl.jellycast.tv.core.playback

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import timber.log.Timber

private const val LOG_INTERVAL_MS = 2_000L

/**
 * Debug only: tells whether buffering comes from the network. Compare with EventLogger
 * `loading [true]`: the rate drops by itself while loading is paused on a full buffer.
 */
@OptIn(UnstableApi::class)
internal class ThroughputLogger : TransferListener {
    private var bytes = 0L
    private var windowStart = SystemClock.elapsedRealtime()

    override fun onTransferInitializing(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
    ) = Unit

    override fun onTransferStart(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (isNetwork) Timber.i("Network: connection open from byte %d", dataSpec.position)
    }

    override fun onBytesTransferred(
        source: DataSource,
        dataSpec: DataSpec,
        isNetwork: Boolean,
        bytesTransferred: Int,
    ) {
        if (!isNetwork) return
        bytes += bytesTransferred
        val now = SystemClock.elapsedRealtime()
        val elapsed = now - windowStart
        if (elapsed >= LOG_INTERVAL_MS) {
            Timber.i(
                "Network: %d Mbps (%d MB in %d ms)",
                bytes * 8 / elapsed / 1_000,
                bytes shr 20,
                elapsed,
            )
            bytes = 0
            windowStart = now
        }
    }

    override fun onTransferEnd(source: DataSource, dataSpec: DataSpec, isNetwork: Boolean) {
        if (isNetwork) Timber.i("Network: connection closed (from byte %d)", dataSpec.position)
    }
}
