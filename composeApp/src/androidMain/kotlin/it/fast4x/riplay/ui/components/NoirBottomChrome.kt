package it.fast4x.riplay.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PlayerPosition
import it.fast4x.riplay.extensions.ads.BannerAd
import it.fast4x.riplay.extensions.preferences.playerPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.navigation.nav.HorizontalNavigationBar
import it.fast4x.riplay.ui.screens.search.openHomeTab
import kotlinx.coroutines.delay

/**
 * State shared between the persistent bottom chrome and the screens around it.
 *
 * The nav pill and the mini player are drawn once, outside the NavHost (see [NoirBottomChrome]),
 * so they stay still while pages come and go. Screens only tell it whether they want it
 * ([RegisterNoirBottomChrome]) and, for Home, which tab is on show.
 */
object NoirChromeState {
    /** Number of composed screens that rely on the chrome (two while a transition runs). */
    var hosts by mutableIntStateOf(0)

    /** Tab Home shows, written by HomeScreen; the pill highlights it while Home is the route. */
    var homeTab by mutableIntStateOf(0)
}

/** Marks the calling screen as one that wants the persistent bottom chrome. */
@Composable
fun RegisterNoirBottomChrome() {
    DisposableEffect(Unit) {
        NoirChromeState.hosts++
        onDispose { NoirChromeState.hosts-- }
    }
}

/**
 * The floating nav pill and the mini player, one instance for the whole app. Place it after the
 * NavHost and before the full player sheet, inside a full-size parent.
 */
@Composable
fun NoirBottomChrome(
    navController: NavController,
    miniPlayer: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
    val navAtBottom = NavigationBarPosition.Bottom.isCurrent()
    val playerAtBottom = playerPosition != PlayerPosition.Top

    // A screen swap briefly composes two hosts, and a route with none (login, sheets) hides the
    // chrome. The short delay keeps a one-frame gap between screens from blinking it.
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val wanted = NoirChromeState.hosts > 0
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(wanted) {
        if (wanted) shown = true else {
            delay(150)
            shown = false
        }
    }

    val selectedTab = when {
        route == NavRoutes.home.name -> NoirChromeState.homeTab
        route?.substringBefore('?') == NavRoutes.search.name -> 3
        else -> -1
    }

    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = shown,
            enter = EnterTransition.None,
            exit = fadeOut(tween(150)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Column(Modifier.noirContentWidth(NoirContentMax.Chrome)) {
                if (playerAtBottom) {
                    // Yammbo Noir: the mini player floats 12dp above the nav pill.
                    // Without the pill below, nothing else keeps it clear of the system bar.
                    Column(
                        Modifier
                            .then(if (navAtBottom) Modifier else Modifier.windowInsetsPadding(WindowInsets.navigationBars))
                            .padding(top = 5.dp, bottom = 12.dp)
                    ) {
                        BannerAd()
                        miniPlayer()
                    }
                }
                if (navAtBottom) {
                    val navigationBar = HorizontalNavigationBar(
                        tabIndex = selectedTab,
                        onTabChanged = { index -> openHomeTab(navController, context, index) },
                        navController = navController
                    )
                    navigationBar.add { Item ->
                        Item(0, stringResource(R.string.home), R.drawable.home)
                        Item(1, stringResource(R.string.live_radio_tab), R.drawable.radio)
                        Item(5, stringResource(R.string.podcasts_tab_podcasts), R.drawable.podcasts_tab)
                        Item(2, stringResource(R.string.my_music), R.drawable.musical_notes)
                        Item(3, stringResource(R.string.search), R.drawable.search)
                    }
                    navigationBar.Draw()
                }
            }
        }
    }
}
