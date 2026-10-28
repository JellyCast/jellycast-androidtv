package nl.jellycast.mobile.navigation

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationItemIconPosition
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarArrangement
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.ShortNavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigation3.ui.NavDisplay
import nl.jellycast.mobile.R
import nl.jellycast.mobile.core.designsystem.theme.JellyCastColors
import nl.jellycast.mobile.core.designsystem.theme.LocalLayout
import nl.jellycast.mobile.core.library.LibraryStore
import nl.jellycast.mobile.feature.downloads.DownloadedSeriesScreen
import nl.jellycast.mobile.feature.downloads.DownloadsScreen
import nl.jellycast.mobile.feature.home.HomeScreen
import nl.jellycast.mobile.feature.library.LibraryScreen
import nl.jellycast.mobile.feature.lists.AddToListHost
import nl.jellycast.mobile.feature.lists.ListScreen
import nl.jellycast.mobile.feature.lists.ListsScreen
import nl.jellycast.mobile.feature.movie.MovieScreen
import nl.jellycast.mobile.feature.person.PersonScreen
import nl.jellycast.mobile.feature.player.PlayerHost
import nl.jellycast.mobile.feature.series.SeriesScreen
import nl.jellycast.mobile.feature.settings.SettingsHost
import nl.jellycast.mobile.ui.downloads.DownloadAccessory
import nl.jellycast.mobile.ui.remote.RemoteNowPlayingHost
import nl.jellycast.mobile.ui.remote.TvBar
import org.koin.compose.koinInject

/**
 * The signed-in app, as iOS `MainView`: three tabs (Home, Library, Downloads), each with its own
 * back stack; the TV bar or download bar above the tab bar; the player (mini or full) over
 * everything; settings and "Add to list" as sheets. The navigation bar is at the bottom on phones
 * and tablets alike.
 */
@Composable
fun MainShell(modifier: Modifier = Modifier) {
    val navigation = remember { AppNavigation() }
    val store: LibraryStore = koinInject()
    LaunchedEffect(store) { store.start() }

    CompositionLocalProvider(LocalAppNavigation provides navigation) {
        var bottomBarHeightPx by remember { mutableIntStateOf(0) }
        val density = LocalDensity.current
        Box(modifier.fillMaxSize()) {
            // The bar stays at the bottom on tablets too: the design is made for it, and apps such
            // as Netflix and Spotify keep it there instead of Material's side rail.
            Column(Modifier.fillMaxSize()) {
                TabContent(navigation, Modifier.weight(1f))
                Column(Modifier.onSizeChanged { bottomBarHeightPx = it.height }) {
                    AccessoryBar(navigation)
                    TabBar(navigation)
                }
            }
            val bottomInset = with(density) { bottomBarHeightPx.toDp() }
            PlayerHost(bottomInset = bottomInset)
            RemoteNowPlayingHost()
            SettingsHost()
            AddToListHost()
        }
        BackHandler(enabled = navigation.currentStack.size > 1) { navigation.goBack() }
    }
}

@Composable
private fun TabContent(navigation: AppNavigation, modifier: Modifier = Modifier) {
    // Keeps every tab's screens and scroll positions while another tab shows.
    val tabStates = rememberSaveableStateHolder()
    Box(modifier) {
        tabStates.SaveableStateProvider(navigation.tab.name) {
            NavDisplay(
                backStack = navigation.currentStack,
                onBack = { navigation.goBack() },
                transitionSpec = { screenFade() },
                popTransitionSpec = { screenFade() },
                predictivePopTransitionSpec = { screenFade() },
                entryDecorators =
                    listOf(
                        rememberSaveableStateHolderNavEntryDecorator(),
                        rememberViewModelStoreNavEntryDecorator(),
                    ),
                entryProvider =
                    entryProvider {
                        entry<TabRoot> {
                            when (navigation.tab) {
                                AppTab.Home -> HomeScreen()
                                AppTab.Library -> LibraryScreen()
                                AppTab.Downloads -> DownloadsScreen()
                            }
                        }
                        entry<MovieRoute> { MovieScreen(it.movieId) }
                        entry<SeriesRoute> { SeriesScreen(it.seriesId) }
                        entry<PersonRoute> { PersonScreen(it.personId, it.name) }
                        entry<DownloadedSeriesRoute> { DownloadedSeriesScreen(it.seriesId) }
                        entry<ListsRoute> { ListsScreen() }
                        entry<ListRoute> { ListScreen(it.listId) }
                    },
            )
        }
    }
}

/** The TV bar wins over the download bar; the download bar hides on the Downloads tab. */
@Composable
private fun AccessoryBar(navigation: AppNavigation) {
    val tvShown = TvBar(Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    if (!tvShown && navigation.tab != AppTab.Downloads) {
        DownloadAccessory(
            onOpenDownloads = { navigation.select(AppTab.Downloads) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

private data class TabItem(val tab: AppTab, @StringRes val label: Int, @DrawableRes val icon: Int)

private val TabItems =
    listOf(
        TabItem(AppTab.Home, R.string.tab_home, R.drawable.ic_home),
        TabItem(AppTab.Library, R.string.tab_library, R.drawable.ic_library),
        TabItem(AppTab.Downloads, R.string.tab_downloads, R.drawable.ic_download),
    )

@Composable
private fun TabBar(navigation: AppNavigation) {
    // On a tablet the three tabs stay together in the middle, icon beside the label.
    val wide = LocalLayout.current.isRegular
    val arrangement =
        if (wide) {
            ShortNavigationBarArrangement.Centered
        } else {
            ShortNavigationBarArrangement.EqualWeight
        }
    val iconPosition =
        if (wide) NavigationItemIconPosition.Start else NavigationItemIconPosition.Top
    ShortNavigationBar(
        containerColor = JellyCastColors.Surface,
        arrangement = arrangement,
    ) {
        TabItems.forEach { item ->
            ShortNavigationBarItem(
                selected = navigation.tab == item.tab,
                onClick = { navigation.select(item.tab) },
                icon = { Icon(painterResource(item.icon), contentDescription = null) },
                label = { Text(stringResource(item.label)) },
                iconPosition = iconPosition,
                colors =
                    ShortNavigationBarItemDefaults.colors(
                        selectedIconColor = JellyCastColors.Accent,
                        selectedTextColor = JellyCastColors.Accent,
                        selectedIndicatorColor = JellyCastColors.Rail,
                    ),
            )
        }
    }
}

private const val SCREEN_FADE_MS = 200

private fun screenFade(): ContentTransform =
    fadeIn(tween(SCREEN_FADE_MS)) togetherWith fadeOut(tween(SCREEN_FADE_MS))
