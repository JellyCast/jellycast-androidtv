package nl.jellycast.tv.core.model

/** What another device (the iOS app) asks the player to do, through the server. */
sealed interface RemoteCommand {
    data object Pause : RemoteCommand

    data object Unpause : RemoteCommand

    data object PlayPause : RemoteCommand

    data object Stop : RemoteCommand

    data object NextEpisode : RemoteCommand

    /**
     * Replaces what plays; [startPositionMs] is where the phone wants it to start, [streams] the
     * tracks it played there.
     */
    data class Play(
        val itemId: String,
        val startPositionMs: Long,
        val streams: StreamChoice = StreamChoice(null, null),
    ) : RemoteCommand

    data class Seek(val positionMs: Long) : RemoteCommand

    /** Jellyfin stream index, counted over all streams of the media source. */
    data class SetAudioStream(val index: Int) : RemoteCommand

    /** Jellyfin stream index; [NO_STREAM] turns subtitles off. */
    data class SetSubtitleStream(val index: Int) : RemoteCommand

    companion object {
        const val NO_STREAM = -1
    }
}
