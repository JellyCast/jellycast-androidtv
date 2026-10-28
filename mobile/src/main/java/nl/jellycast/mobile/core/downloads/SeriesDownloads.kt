package nl.jellycast.mobile.core.downloads

import nl.jellycast.core.model.Episode
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.mobile.core.library.LibraryIndex
import nl.jellycast.mobile.core.library.SeriesProgress

/** One episode's default choice and size, from the server's file info. */
data class EpisodePlan(val episode: Episode, val kind: DownloadKind, val bytes: Long?)

/** The unwatched episodes from where the series stands, in order; specials are left out. */
fun upcomingEpisodes(progress: SeriesProgress, count: Int): List<Episode> {
    val regular = progress.episodes.filter { (it.seasonNumber ?: 0) > 0 }
    val start = progress.next?.let { next -> regular.indexOfFirst { it.id == next.id } }
        ?.takeIf { it >= 0 } ?: 0
    return regular.drop(start).filter { !it.isPlayed }.take(count)
}

/** The upcoming episodes this device doesn't have or fetch yet. */
fun DownloadStore.missingEpisodes(
    progress: SeriesProgress,
    count: Int = DownloadSettings.SERIES_BATCH,
): List<Episode> = upcomingEpisodes(progress, count).filter { download(it.id) == null }

suspend fun DownloadStore.plans(episodes: List<Episode>): List<EpisodePlan> = episodes.map {
    val plan = plan(PlayableItem.EpisodeItem(it)).plan
    EpisodePlan(it, plan.defaultKind, plan.bytes(plan.defaultKind))
}

/** Queues the episodes in order as far as the free space reaches; returns how many went in. */
fun DownloadStore.enqueue(plans: List<EpisodePlan>): Int {
    var free = freeBytes() ?: Long.MAX_VALUE
    var queued = 0
    for (plan in plans) {
        val bytes = plan.bytes ?: 0
        if (bytes > free) break
        free -= bytes
        enqueue(PlayableItem.EpisodeItem(plan.episode), plan.kind, plan.bytes)
        queued++
    }
    return queued
}

/**
 * Run after every library sync: removes what was watched a day ago, and at home tops up the
 * kept series.
 */
suspend fun DownloadStore.tidy(index: LibraryIndex) {
    if (settings.removesWatched.value) {
        downloads.value
            .filter { it.status == DownloadStatus.Done && isWatchedLongAgo(it, index) }
            .forEach { remove(it.id) }
    }
    if (!settings.keepsSeriesAhead.value || !canReachServer.value) return
    for (seriesId in settings.keptSeriesIds.value) {
        val progress = index.progress(seriesId) ?: continue
        val missing = missingEpisodes(progress)
        if (missing.isNotEmpty()) enqueue(plans(missing))
    }
}

/** Only a watch after the download counts, so an item downloaded to see again stays. */
private fun DownloadStore.isWatchedLongAgo(download: Download, index: LibraryIndex): Boolean {
    val snapshot = index.snapshot
    val (played, lastPlayedAt) =
        snapshot.moviesById[download.id]?.let { it.isPlayed to it.lastPlayedAt }
            ?: snapshot.episodesById[download.id]?.let { it.isPlayed to it.lastPlayedAt }
            ?: return false
    if (!played || lastPlayedAt == null || lastPlayedAt <= download.addedAtMs) return false
    return nowMs() - lastPlayedAt > DownloadSettings.WATCHED_GRACE_MS
}
