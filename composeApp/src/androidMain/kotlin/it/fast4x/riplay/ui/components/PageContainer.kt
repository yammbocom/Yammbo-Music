package it.fast4x.riplay.ui.components

import it.fast4x.riplay.ui.components.themed.noirBack
import it.fast4x.riplay.ui.components.themed.NoirTopBar
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.PlayerPosition
import it.fast4x.riplay.enums.TransitionEffect
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.extensions.preferences.homeScreenTabIndexKey
import it.fast4x.riplay.extensions.preferences.playerPositionKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.transitionEffectKey
import it.fast4x.riplay.ui.components.navigation.header.AppHeader
import it.fast4x.riplay.ui.components.navigation.nav.HorizontalNavigationBar
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.extensions.preferences.preferences

@Composable
fun PageContainer(
    modifier: Modifier = Modifier,
    navController: NavController,
    miniPlayer: @Composable () -> Unit = {},
    /** Set false on artwork-led screens (artist, album) so the cover can run to the top edge. */
    showTopBar: Boolean = true,
    /** Yammbo Noir minimal top row (back, optional title, actions) instead of the logo bar. */
    noirTopBar: Boolean = false,
    topBarTitle: String? = null,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable AnimatedVisibilityScope.(Int) -> Unit
) {
    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.SharedAxis)
    val playerPosition by rememberPreference(playerPositionKey, PlayerPosition.Bottom)

    // The nav pill and the bottom mini player are drawn once by NoirBottomChrome, outside the
    // NavHost, so they do not move with the page transition.
    RegisterNoirBottomChrome()

    androidx.compose.material3.Scaffold(
        modifier = modifier,
        containerColor = colorPalette().background0,
        topBar = {
            if( noirTopBar )
                NoirTopBar(
                    onBack = { navController.noirBack() },
                    title = topBarTitle,
                    actions = topBarActions
                )
            else if( showTopBar && UiType.RiPlay.isCurrent() )
                AppHeader( navController ).Draw()
        }
    ) {
        //**
        Box(
            modifier = Modifier
                // Content runs under the floating nav pill; see ScreenContainer.
                .padding(top = it.calculateTopPadding())
                .fillMaxSize()
        ) {

            Row(
                modifier = modifier
                    .background(colorPalette().background0)
                    .fillMaxSize()
            ) {
                val topPadding = if ( UiType.ViMusic.isCurrent() ) 30.dp else 0.dp

                AnimatedContent(
                    targetState = 0,
                    transitionSpec = {
                        when (transitionEffect) {
                            TransitionEffect.None -> EnterTransition.None togetherWith ExitTransition.None
                            TransitionEffect.Expand -> expandIn(
                                animationSpec = tween(
                                    350,
                                    easing = LinearOutSlowInEasing
                                ), expandFrom = Alignment.BottomStart
                            ).togetherWith(
                                shrinkOut(
                                    animationSpec = tween(
                                        350,
                                        easing = FastOutSlowInEasing
                                    ), shrinkTowards = Alignment.CenterStart
                                )
                            )

                            TransitionEffect.Fade, TransitionEffect.SharedAxis -> fadeIn(animationSpec = tween(350)).togetherWith(
                                fadeOut(animationSpec = tween(350))
                            )

                            TransitionEffect.Scale -> scaleIn(animationSpec = tween(350)).togetherWith(
                                scaleOut(animationSpec = tween(350))
                            )

                            TransitionEffect.SlideHorizontal, TransitionEffect.SlideVertical -> {
                                val slideDirection = when (targetState > initialState) {
                                    true -> {
                                        if (transitionEffect == TransitionEffect.SlideHorizontal)
                                            AnimatedContentTransitionScope.SlideDirection.Left
                                        else AnimatedContentTransitionScope.SlideDirection.Up
                                    }

                                    false -> {
                                        if (transitionEffect == TransitionEffect.SlideHorizontal)
                                            AnimatedContentTransitionScope.SlideDirection.Right
                                        else AnimatedContentTransitionScope.SlideDirection.Down
                                    }
                                }

                                val animationSpec = spring(
                                    dampingRatio = 0.9f,
                                    stiffness = Spring.StiffnessLow,
                                    visibilityThreshold = IntOffset.VisibilityThreshold
                                )

                                slideIntoContainer(slideDirection, animationSpec) togetherWith
                                        slideOutOfContainer(slideDirection, animationSpec)
                            }
                        }
                    },
                    label = "",
                    modifier = Modifier
                        //.fillMaxWidth()
                        .fillMaxHeight()
                        .padding(top = topPadding),
                    content = content
                )
            }
            //**
            // A bottom mini player belongs to NoirBottomChrome; only a top one is drawn here.
            if (playerPosition == PlayerPosition.Top)
                Box(
                    modifier = Modifier
                        .padding(top = 5.dp, bottom = 12.dp)
                        .align(Alignment.TopCenter)
                ) {
                    miniPlayer.invoke()
                }
        }
    }
}
