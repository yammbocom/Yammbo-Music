package it.fast4x.riplay.ui.components

import it.fast4x.riplay.ui.components.themed.noirBack
import it.fast4x.riplay.ui.components.themed.NoirTopBar
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.material.BottomNavigationDefaults.windowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import it.fast4x.riplay.extensions.ads.BannerAd
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.PlayerPosition
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.extensions.preferences.playerPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.navigation.header.AppHeader
import it.fast4x.riplay.ui.components.navigation.nav.AbstractNavigationBar
import it.fast4x.riplay.ui.components.navigation.nav.HorizontalNavigationBar
import it.fast4x.riplay.ui.components.navigation.nav.VerticalNavigationBar
import it.fast4x.riplay.utils.transition

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScreenContainer(
    navController: NavController,
    tabIndex: Int = 0,
    onTabChanged: (Int) -> Unit = {},
    miniPlayer: @Composable (() -> Unit)? = null,
    navBarContent: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit,
    // False hides the tab bar, for screens that draw their tabs in the page (search results).
    showNavigationBar: Boolean = true,
    // The bar can drive something other than the content: Search shows the app's main tabs
    // while its own modes are chips in the page.
    navBarTabIndex: Int = tabIndex,
    onNavBarTabChanged: (Int) -> Unit = onTabChanged,
    // True for the app's main screens (Home, Search): the bottom nav pill and the bottom mini
    // player come from the persistent NoirBottomChrome instead of being drawn here.
    sharedChrome: Boolean = false,
    // True swaps the legacy logo bar for the Noir minimal top row (back arrow only), for pages
    // that draw their own Noir title.
    noirTopBar: Boolean = false,
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val navigationBar: AbstractNavigationBar =
        when( NavigationBarPosition.current() ) {
            NavigationBarPosition.Left, NavigationBarPosition.Right ->
                VerticalNavigationBar( navBarTabIndex, onNavBarTabChanged, navController )
            NavigationBarPosition.Top, NavigationBarPosition.Bottom ->
                HorizontalNavigationBar( navBarTabIndex, onNavBarTabChanged, navController )
        }
    navigationBar.add( navBarContent )

    if( sharedChrome )
        RegisterNoirBottomChrome()

    val appHeader: @Composable () -> Unit = {
        Column(
            verticalArrangement = Arrangement.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            if( noirTopBar )
                NoirTopBar( onBack = { navController.noirBack() } )
            else if( UiType.RiPlay.isCurrent() )
                AppHeader( navController ).Draw()


            if ( NavigationBarPosition.Top.isCurrent() && showNavigationBar )
                navigationBar.Draw()
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val modifier: Modifier =
        if( UiType.ViMusic.isCurrent() && navigationBar is HorizontalNavigationBar)
            Modifier
        else
            Modifier.nestedScroll( scrollBehavior.nestedScrollConnection )

    Scaffold(
        modifier = modifier,
        containerColor = colorPalette().background0,
        topBar = appHeader,
        bottomBar = {
            if ( NavigationBarPosition.Bottom.isCurrent() && showNavigationBar && !sharedChrome )
                navigationBar.Draw()
        }
    ) {
        val paddingSides = WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
        val innerPadding =
            if( NavigationBarPosition.Top.isCurrent() )
                windowInsets.only( paddingSides ).asPaddingValues()
            else
                PaddingValues( Dp.Hairline )

        Box(
            Modifier
                // Only the top inset: the content runs under the floating nav pill so the page
                // shows through around it instead of stopping at an opaque strip.
                .padding(top = it.calculateTopPadding())
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            Row(
                Modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {
                if( NavigationBarPosition.Left.isCurrent() )
                    navigationBar.Draw()

                val topPadding = if ( UiType.ViMusic.isCurrent() ) 30.dp else 0.dp
                AnimatedContent(
                    targetState = tabIndex,
                    transitionSpec = transition(),
                    content = { idx ->
                        // Each target gets its own entry time so the cascade replays per tab.
                        val enteredAt = remember { SystemClock.uptimeMillis() }
                        CompositionLocalProvider(LocalScreenEnteredAt provides enteredAt) {
                            content(idx)
                        }
                    },
                    label = "",
                    modifier = Modifier.fillMaxHeight().padding( top = topPadding )
                )

                if( NavigationBarPosition.Right.isCurrent() )
                    navigationBar.Draw()
            }

            val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)
            val playerAlignment =
                if (playerPosition == PlayerPosition.Top)
                    Alignment.TopCenter
                else
                    Alignment.BottomCenter

            // The shared chrome owns the bottom mini player; only a top one is drawn here.
            if( !sharedChrome || playerPosition == PlayerPosition.Top )
                Column(
                    Modifier
                        .padding(bottom = it.calculateBottomPadding())
                        // Yammbo Noir: the mini player floats 12dp above the nav pill.
                        .padding(top = 5.dp, bottom = 12.dp)
                        .align( playerAlignment ),
                ) {
                    BannerAd()
                    miniPlayer?.invoke()
                }
        }
    }
}