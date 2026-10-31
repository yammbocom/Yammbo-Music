package it.fast4x.riplay.utils

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.shrinkOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.IntOffset
import it.fast4x.riplay.enums.TransitionEffect
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.transitionEffectKey
import it.fast4x.riplay.ui.components.EmphasizedEasing

private val tween350 = tween<Float>( 350 )

private fun slideDirection(
    transitionEffect: TransitionEffect,
    targetState: Int,
    initialState: Int
): AnimatedContentTransitionScope.SlideDirection {
    val isSlideHorizontal = transitionEffect == TransitionEffect.SlideHorizontal

    return when ( targetState > initialState ) {
        true ->
            if ( isSlideHorizontal )
                AnimatedContentTransitionScope.SlideDirection.Left
            else
                AnimatedContentTransitionScope.SlideDirection.Up
        false ->
            if ( isSlideHorizontal )
                AnimatedContentTransitionScope.SlideDirection.Right
            else
                AnimatedContentTransitionScope.SlideDirection.Down
    }
}

private fun scale(): ContentTransform = scaleIn( tween350 ) togetherWith scaleOut( tween350 )

private fun fade(): ContentTransform = fadeIn( tween350 ) togetherWith fadeOut( tween350 )

private fun expand(): ContentTransform {
    val expandIn = expandIn(
        tween( 350, 0, LinearOutSlowInEasing ),
        Alignment.TopStart
    )
    val shrinkOut = shrinkOut(
        tween( 350, 0, LinearOutSlowInEasing ),
        Alignment.TopStart
    )
    return expandIn togetherWith shrinkOut
}

// Position of a tab in the bottom bar, left to right. Sub-tabs (>= 10) sit deeper than any top tab.
private fun visualPosition(tab: Int): Int = when (tab) {
    0 -> 0
    1 -> 1
    5 -> 2
    2 -> 3
    3 -> 4
    else -> if (tab >= 10) 100 + tab else tab
}

private fun sharedAxis(initialState: Int, targetState: Int): ContentTransform {
    val dir = if (visualPosition(targetState) >= visualPosition(initialState)) 1 else -1
    val enter = slideInHorizontally(tween(300, easing = EmphasizedEasing)) { (it * 0.08f * dir).toInt() } +
            fadeIn(tween(210, delayMillis = 90, easing = LinearOutSlowInEasing))
    val exit = slideOutHorizontally(tween(300, easing = EmphasizedEasing)) { (-it * 0.08f * dir).toInt() } +
            fadeOut(tween(90, easing = FastOutLinearInEasing))
    return enter togetherWith exit
}

private fun none(): ContentTransform = EnterTransition.None togetherWith ExitTransition.None

@Composable
fun transition(): AnimatedContentTransitionScope<Int>.() -> ContentTransform {

    val transitionEffect by rememberPreference(transitionEffectKey, TransitionEffect.SharedAxis)

    return {
        when( transitionEffect ) {
            TransitionEffect.SharedAxis -> sharedAxis(initialState, targetState)
            TransitionEffect.Scale -> scale()
            TransitionEffect.Fade -> fade()
            TransitionEffect.Expand -> expand()
            TransitionEffect.None -> none()
            TransitionEffect.SlideVertical, TransitionEffect.SlideHorizontal -> {
                val animationSpec = spring(
                    dampingRatio = 0.9f,
                    stiffness = Spring.StiffnessLow,
                    visibilityThreshold = IntOffset.VisibilityThreshold
                )
                val slideDirection = slideDirection( transitionEffect, targetState, initialState )
                slideIntoContainer(slideDirection, animationSpec) togetherWith slideOutOfContainer(slideDirection, animationSpec)
            }
        }
    }
}