package nl.jellycast.mobile.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import nl.jellycast.core.model.Artwork
import nl.jellycast.core.model.MediaTech
import nl.jellycast.core.model.Movie
import nl.jellycast.core.model.PlayableItem
import nl.jellycast.core.model.Spine
import nl.jellycast.mobile.core.designsystem.theme.JellyCastTheme

private fun previewMovie(id: String, title: String, resumeMs: Long) = Movie(
    id = id,
    title = title,
    year = 2014,
    runtimeMs = 10_140_000,
    overview = null,
    genres = emptyList(),
    ageRating = null,
    resumePositionMs = resumeMs,
    lastPlayedAt = null,
    addedAt = null,
    artwork = Artwork(),
    spine = Spine(posterUrl = null, logoUrl = null, color = 0xFF000000, isLight = false),
    tech = MediaTech(),
)

private val PreviewTiles =
    listOf(
        HomeTile.Continue(PlayableItem.MovieItem(previewMovie("1", "Interstellar", 6_000_000))),
        HomeTile.Continue(PlayableItem.MovieItem(previewMovie("2", "Arrival", 900_000))),
        HomeTile.Continue(PlayableItem.MovieItem(previewMovie("3", "Dune", 3_000_000))),
    )

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeContentPreview() {
    JellyCastTheme {
        HomeContent(
            uiState =
                HomeUiState(
                    content = HomeContent.Ready(PreviewTiles),
                    syncProblem = SyncProblem(null, 0, isRetrying = false, isOffline = true),
                ),
            onPlay = {},
            onOpen = {},
            onShowActions = {},
            onOpenSettings = {},
            onRefresh = {},
            onRetry = {},
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun HomeSkeletonPreview() {
    JellyCastTheme { HomeContent(HomeUiState(), {}, {}, {}, {}, {}, {}) }
}
