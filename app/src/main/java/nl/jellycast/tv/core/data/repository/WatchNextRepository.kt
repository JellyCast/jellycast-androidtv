package nl.jellycast.tv.core.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.SQLException
import android.media.tv.TvContract.WatchNextPrograms
import android.net.Uri
import androidx.core.content.contentValuesOf
import kotlinx.coroutines.withContext
import nl.jellycast.tv.core.common.AppDispatchers
import nl.jellycast.tv.core.model.WatchNextEntry
import nl.jellycast.tv.core.model.WatchNextKind
import timber.log.Timber

/** The "Continue watching" row of the Google TV home screen. Failures are logged, never thrown. */
interface WatchNextRepository {
    /** Replaces this app's cards with [entries]. */
    suspend fun publish(entries: List<WatchNextEntry>)

    suspend fun clear()
}

class TvProviderWatchNextRepository(
    private val context: Context,
    private val dispatchers: AppDispatchers,
) : WatchNextRepository {
    private class Card(val rowId: Long, val isBrowsable: Boolean, val lastEngagementMs: Long)

    override suspend fun publish(entries: List<WatchNextEntry>) = guarded { resolver ->
        val existing = ownCards(resolver)
        val wanted = entries.mapTo(mutableSetOf()) { it.itemId }
        existing
            .filterKeys { it !in wanted }
            .values
            .forEach { delete(resolver, it) }
        entries.forEach { write(resolver, it, existing[it.itemId]) }
        Timber.d("Watch Next: %d cards", ownCards(resolver).size)
    }

    override suspend fun clear() = guarded { resolver ->
        ownCards(resolver).values.forEach { delete(resolver, it) }
    }

    private suspend fun guarded(block: (ContentResolver) -> Unit) = withContext(dispatchers.io) {
        try {
            block(context.contentResolver)
        } catch (e: SecurityException) {
            Timber.w(e, "Watch Next not writable")
        } catch (e: IllegalArgumentException) {
            Timber.w(e, "Watch Next provider missing")
        } catch (e: SQLException) {
            Timber.w(e, "Watch Next update failed")
        }
    }

    // The provider only returns rows this app inserted.
    private fun ownCards(resolver: ContentResolver): Map<String, Card> = resolver
        .query(WatchNextPrograms.CONTENT_URI, CARD_COLUMNS, null, null, null)
        ?.use { cursor ->
            buildMap {
                while (cursor.moveToNext()) {
                    val itemId = cursor.getString(1) ?: continue
                    put(itemId, Card(cursor.getLong(0), cursor.getInt(2) == 1, cursor.getLong(3)))
                }
            }
        }.orEmpty()

    private fun write(resolver: ContentResolver, entry: WatchNextEntry, old: Card?) {
        val values = entry.toContentValues()
        when {
            old == null -> {
                resolver.insert(WatchNextPrograms.CONTENT_URI, values)
            }

            old.isBrowsable -> {
                resolver.update(rowUri(old), values, null, null)
            }

            // The user removed the card; only watching it again brings it back. The browsable
            // flag belongs to the system, so a new row replaces the hidden one.
            entry.lastEngagementMs > old.lastEngagementMs -> {
                delete(resolver, old)
                resolver.insert(WatchNextPrograms.CONTENT_URI, values)
            }
        }
    }

    private fun delete(resolver: ContentResolver, card: Card) {
        resolver.delete(rowUri(card), null, null)
    }

    private fun rowUri(card: Card): Uri =
        ContentUris.withAppendedId(WatchNextPrograms.CONTENT_URI, card.rowId)
}

private val CARD_COLUMNS =
    arrayOf(
        WatchNextPrograms._ID,
        WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID,
        WatchNextPrograms.COLUMN_BROWSABLE,
        WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS,
    )

private fun WatchNextEntry.toContentValues(): ContentValues = contentValuesOf(
    WatchNextPrograms.COLUMN_WATCH_NEXT_TYPE to
        when (kind) {
            WatchNextKind.Continue -> WatchNextPrograms.WATCH_NEXT_TYPE_CONTINUE
            WatchNextKind.Next -> WatchNextPrograms.WATCH_NEXT_TYPE_NEXT
        },
    WatchNextPrograms.COLUMN_TYPE to
        if (episode == null) WatchNextPrograms.TYPE_MOVIE else WatchNextPrograms.TYPE_TV_EPISODE,
    WatchNextPrograms.COLUMN_TITLE to title,
    WatchNextPrograms.COLUMN_EPISODE_TITLE to episode?.title,
    WatchNextPrograms.COLUMN_SEASON_DISPLAY_NUMBER to episode?.seasonNumber?.toString(),
    WatchNextPrograms.COLUMN_EPISODE_DISPLAY_NUMBER to episode?.episodeNumber?.toString(),
    WatchNextPrograms.COLUMN_DURATION_MILLIS to durationMs?.toInt(),
    WatchNextPrograms.COLUMN_LAST_PLAYBACK_POSITION_MILLIS to positionMs.toInt(),
    WatchNextPrograms.COLUMN_LAST_ENGAGEMENT_TIME_UTC_MILLIS to lastEngagementMs,
    WatchNextPrograms.COLUMN_POSTER_ART_URI to imageUrl,
    WatchNextPrograms.COLUMN_POSTER_ART_ASPECT_RATIO to WatchNextPrograms.ASPECT_RATIO_16_9,
    WatchNextPrograms.COLUMN_INTENT_URI to playLink,
    WatchNextPrograms.COLUMN_INTERNAL_PROVIDER_ID to itemId,
)
