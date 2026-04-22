package it.fast4x.riplay.ui.components.navigation.nav

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarType
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.utils.isLandscape
import it.fast4x.riplay.utils.isTVDevice
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.ui.components.themed.Button
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.showSearchIconInNav
import it.fast4x.riplay.utils.typography

// TODO: Move this to where it belongs. Currently, UNKNOWN
fun Modifier.vertical( enabled: Boolean = true ) =
    if ( enabled )
        layout { measurable, constraints ->
            val c: Constraints = constraints.copy( maxWidth = Int.MAX_VALUE )
            val placeable = measurable.measure( c )

            layout( placeable.height, placeable.width ) {
                placeable.place(
                    x = -(placeable.width / 2 - placeable.height / 2),
                    y = -(placeable.height / 2 - placeable.width / 2)
                )
            }
        }
    else this

// Shown when "Navigation bar position" is set to "left" or "right"
class VerticalNavigationBar(
    val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
): AbstractNavigationBar( navController, modifier ) {

    @Composable
    private fun addButton( component: @Composable () -> Unit ) =
        // buttonList() duplicates button instead of updating them
        // Do NOT use it
        super.buttonList.add( component )

    @Composable
    override fun add(buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit ) {
        buttonList.clear()
        val transition = updateTransition( targetState = tabIndex, label = null )
        val isLandscape: Boolean = isLandscape

        buttons { index, text, iconId ->
            val textColor by transition.animateColor(label = "") {
                if (it == index)
                    colorPalette().text
                else
                    colorPalette().textDisabled
            }
            val dothAlpha by transition.animateFloat(label = "") {
                if (it == index)
                    1f
                else
                    0f
            }

            val textContent: @Composable () -> Unit = {
                val isTvCtx = isTVDevice()
                if ( isTvCtx ) {
                    // On TV, always show text horizontally beside the icon — never rotated
                    BasicText(
                        text = text,
                        style = TextStyle(
                            fontSize = typography().s.semiBold.fontSize,
                            fontWeight = typography().s.semiBold.fontWeight,
                            color = if (tabIndex == index) colorPalette().text else colorPalette().textSecondary,
                        ),
                        modifier = Modifier.padding(start = 14.dp)
                    )
                } else if ( NavigationBarType.IconAndText.isCurrent() ) {
                    BasicText(
                        text = text,
                        style = TextStyle(
                            fontSize = typography().xs.semiBold.fontSize,
                            fontWeight = typography().xs.semiBold.fontWeight,
                            color = colorPalette().text,
                        ),
                        modifier = Modifier.vertical( enabled = !isLandscape )
                                    .rotate(if (isLandscape) 0f else -90f)
                                    .padding(horizontal = 16.dp)
                    )
                }
            }

            val buttonModifier: Modifier =
                if ( NavigationBarType.IconOnly.isCurrent() ) {
                    Modifier
                        .padding( top = 12.dp, bottom = 12.dp )
                            .size(24.dp)
                } else {
                    Modifier.vertical( enabled = !isLandscape )
                            .size( Dimensions.navigationRailIconOffset * 3 )
                            .graphicsLayer {
                                alpha = dothAlpha
                                translationX = (1f - dothAlpha) * -48.dp.toPx()
                                rotationZ = if (isLandscape) 0f else -90f
                            }
                }
            val button = Button( iconId, textColor, 0.dp, 0.dp, Dp.Unspecified, buttonModifier )
            val isTv = isTVDevice()
            val isSelected = tabIndex == index
            val isFirstItem = index == 0
            val result: @Composable () -> Unit = {
                var isFocused by remember { mutableStateOf(false) }
                val focusRequester = remember { FocusRequester() }
                if (isTv && isFirstItem) {
                    LaunchedEffect(Unit) {
                        try {
                            focusRequester.requestFocus()
                        } catch (e: Exception) {
                            // Focus may not be available yet; ignore safely
                        }
                    }
                }
                val targetScale = if (isTv && isFocused) 1.08f else 1f
                val animatedScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
                    label = "navItemScale"
                )

                val baseModifier = Modifier
                    .padding(
                        horizontal = if (isTv) 10.dp else 8.dp,
                        vertical = if (isTv) 6.dp else 4.dp
                    )
                    .graphicsLayer {
                        scaleX = animatedScale
                        scaleY = animatedScale
                    }
                    .clip(RoundedCornerShape(if (isTv) 16.dp else 24.dp))
                    .background(
                        when {
                            isTv && isFocused -> colorPalette().accent.copy(alpha = 0.45f)
                            isTv && isSelected -> colorPalette().accent.copy(alpha = 0.18f)
                            else -> Color.Transparent
                        }
                    )

                val contentModifier = baseModifier
                    .let { m ->
                        if (isTv && isFocused)
                            m.border(
                                width = 2.dp,
                                color = colorPalette().accent,
                                shape = RoundedCornerShape(16.dp)
                            )
                        else m
                    }
                    .let { m ->
                        if (isTv && isFirstItem) m.focusRequester(focusRequester) else m
                    }
                    .clickable(onClick = { onTabChanged(index) })
                    .let { m ->
                        if (isTv) m.onFocusChanged { isFocused = it.isFocused } else m
                    }
                    .let { m ->
                        if (isTv) m.fillMaxWidth() else m
                    }
                    .padding(
                        vertical = if (isTv) 14.dp else 8.dp,
                        horizontal = if (isTv) 16.dp else 0.dp
                    )

                if (isTv) {
                    // On TV: always horizontal Row with a left pill indicator for the active item
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = contentModifier
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isSelected || isFocused) colorPalette().accent
                                    else Color.Transparent
                                )
                        )
                        Spacer(Modifier.width(10.dp))
                        button.Draw()
                        textContent()
                    }
                } else if (isLandscape) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = contentModifier
                    ) {
                        button.Draw()
                        textContent()
                    }
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = contentModifier
                    ) {
                        button.Draw()
                        textContent()
                    }
                }
            }

            addButton( result )
        }
    }

    @Composable
    override fun BackButton(): NavigationButton {
        val button = super.BackButton()
        button.modifier {
            it.offset( 0.dp, 7.dp )
              .clip( CircleShape )
              .padding( top = 12.dp, bottom = 12.dp )
              .size( 24.dp )
        }
        return button
    }

    @Composable
    override fun SettingsButton(): NavigationButton {
        val button = super.SettingsButton()
        button.modifier {
            it.offset( 0.dp, 7.dp )
              .clip( CircleShape )
              .padding( top = 12.dp, bottom = 12.dp )
              .size( 24.dp )
        }
        return button
    }

    @Composable
    override fun StatsButton(): NavigationButton {
        val button = super.StatsButton()
        button.modifier {
            it.offset( 0.dp, 7.dp )
              .clip( CircleShape )
              .padding( top = 12.dp, bottom = 12.dp )
              .size( 24.dp )
        }
        return button
    }

    @Composable
    override fun SearchButton(): NavigationButton {
        val button = super.SearchButton()
        button.modifier {
            it.offset( 0.dp, 7.dp )
                .clip( CircleShape )
                .padding( top = 12.dp, bottom = 12.dp )
                .size( 24.dp )
        }
        return button
    }

    @Composable
    override fun Draw() {
        val isTv = isTVDevice()
        val tvBackground: Modifier = if (isTv) {
            Modifier.background(
                Brush.verticalGradient(
                    colors = listOf(
                        colorPalette().background1,
                        colorPalette().background0
                    )
                )
            )
        } else Modifier.background(Color.Transparent)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = modifier
                .width(if (isTv) 240.dp else Dimensions.navigationRailWidth)
                .then(tvBackground)
                .verticalScroll( rememberScrollState() )
        ) {
            if (isTv) {
                // Branded header: logo + wordmark at the top of the TV sidebar
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 20.dp, end = 16.dp, top = 28.dp, bottom = 24.dp)
                ) {
                    Image(
                        painter = painterResource(com.yambo.music.R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    BasicText(
                        text = "Yammbo Music",
                        style = TextStyle(
                            fontSize = typography().m.semiBold.fontSize,
                            fontWeight = typography().m.semiBold.fontWeight,
                            color = colorPalette().text
                        )
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
            val boxPadding: Dp =
                if( UiType.ViMusic.isCurrent() )
                    50.dp
                else
                    Dp.Hairline
            Box(
                contentAlignment = Alignment.TopCenter,
                modifier = Modifier
                    /*
                    .height(
                        if( UiType.ViMusic.isCurrent() )
                            if ( showStatsIconInNav() )
                                Dimensions.headerHeight
                            else
                                Dimensions.halfheaderHeight
                        else 0.dp
                    )*/
                    .padding( top = boxPadding )

            ) {
                // Show settings and statistics buttons in homepage
                // Show back button in other screens
//                if( navController.currentBackStackEntry?.destination?.route == NavRoutes.home.name ) {
//                    SettingsButton().Draw()
//                    StatsButton().Draw()
//                } else
//                    BackButton().Draw()
                if(navController.currentBackStackEntry?.destination?.route != NavRoutes.home.name
                    && UiType.ViMusic.isCurrent())
                    BackButton().Draw()
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                content = { buttonList().forEach { it() } }
            )

            // Only show search icon when UI is ViMusic and
            // setting is turned on

            if( UiType.ViMusic.isCurrent() ) {
                val iconSize: Dp =
                    if( isLandscape )
                        Dimensions.navigationRailWidthLandscape
                    else
                        Dimensions.navigationRailWidth
                //val iconHeight: Dp = Dimensions.halfheaderHeight
                if ( showSearchIconInNav() )
                    Box(
                        contentAlignment = Alignment.TopCenter,
                        modifier = Modifier.size(iconSize),
                        content = {
                            SearchButton().Draw()
                        }
                    )

                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.size(iconSize),
                    content = {
                        StatsButton().Draw()
                    }
                )
                Box(
                    contentAlignment = Alignment.TopCenter,
                    modifier = Modifier.size(iconSize),
                    content = {
                        SettingsButton().Draw()
                    }
                )
            }
        }
    }
}