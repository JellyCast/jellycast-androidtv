package nl.jellycast.mobile.feature.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp

/**
 * Takes `AppNavigation.playRequest`, owns the one play of the app and shows it full screen or
 * as the mini player at the bottom end, [bottomInset] above the bottom edge (tab bar and any
 * accessory bar included). Drawn by the shell over everything.
 *
 * Owner: the player worker. STUB.
 */
@Composable
fun PlayerHost(bottomInset: Dp) = Unit
