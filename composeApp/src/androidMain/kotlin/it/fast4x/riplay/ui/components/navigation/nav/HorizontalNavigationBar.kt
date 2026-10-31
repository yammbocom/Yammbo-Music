package it.fast4x.riplay.ui.components.navigation.nav


import android.annotation.SuppressLint
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.enums.NavigationBarType
import it.fast4x.riplay.enums.UiType
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.ui.components.glassSurface
import it.fast4x.riplay.ui.components.themed.Button
import it.fast4x.riplay.ui.components.themed.TextIconButton
import it.fast4x.riplay.utils.applyIf
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.showSearchIconInNav
import it.fast4x.riplay.utils.showStatsIconInNav
import androidx.compose.animation.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import it.fast4x.riplay.utils.applyIf
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.showSearchIconInNav
import it.fast4x.riplay.utils.showStatsIconInNav

// Shown when "Navigation bar position" is set to "top" or "bottom"
class HorizontalNavigationBar(
    val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) : AbstractNavigationBar(navController, modifier) {

    private fun navButtonProperties(): Modifier {
        val padding: Dp = 4.dp
        val size: Dp = 24.dp
        val border: Shape = CircleShape

        return Modifier.padding(all = padding)
            .size(size)
            .clip(shape = border)
    }

    @Composable
    private fun addButton(button: Button, modifier: Modifier = Modifier) =
        buttonList.add {
            Box(modifier) { button.Draw() }
        }

    @SuppressLint("ComposableNaming")
    @Composable
    private fun addButton(index: Int, button: Button, modifier: Modifier = Modifier) =
        buttonList.add(index) {
            Box(modifier) { button.Draw() }
        }

    @Composable
    private fun bottomPadding(): Dp = 0.dp

    private fun topPadding(): Dp = 0.dp

    @Composable
    override fun add(buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit) {
        buttonsProvider = buttons
    }

    @Composable
    override fun BackButton(): NavigationButton {
        val button = super.BackButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SettingsButton(): NavigationButton {
        val button = super.SettingsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun StatsButton(): NavigationButton {
        val button = super.StatsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SearchButton(): NavigationButton {
        val button = super.SearchButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun Draw() {

        // Stato per l'animazione di entrata
        var visible by remember { mutableStateOf(false) }

        // Attiva l'animazione al primo avvio
        LaunchedEffect(Unit) {
            visible = true
        }

        val isNavbarBottom = NavigationBarPosition.Bottom.isCurrent()
        val density = LocalDensity.current
        val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        val topInset = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

        val contentPadding = if (isNavbarBottom) {
            PaddingValues(bottom = bottomInset)
        } else {
            PaddingValues(top = topInset)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (NavigationBarPosition.Bottom.isCurrent()) Arrangement.Bottom else Arrangement.Top,
            // No fill here: the page now runs under the bar, so anything painted on this
            // full-width slot would hide the content around the floating pill.
            modifier = modifier
                .fillMaxWidth()
        ) {

            // Definizione delle transizioni
            val enterTransition = if (isNavbarBottom) {
                slideInVertically(
                    initialOffsetY = { fullHeight -> fullHeight },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            } else {
                // Se è in alto, scende dall'alto
                slideInVertically(
                    initialOffsetY = { fullHeight -> -fullHeight },
                    animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            }

            AnimatedVisibility(
                visible = visible,
                enter = enterTransition,
                // Opzionale: puoi aggiungere un'animazione di uscita se la barra viene nascosta
                // exit = slideOutVertically() + fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceAround,
                    modifier = Modifier
                        .fillMaxWidth()
                        // Inset from the screen edges so the bar reads as a floating pill
                        // rather than a docked band. The system inset is applied as outer
                        // margin, keeping the glass clear of the gesture handle.
                        .applyIf(isNavbarBottom) {
                            padding(
                                start = 12.dp,
                                end = 12.dp,
                                bottom = bottomInset + 20.dp
                            )
                        }
                        .applyIf(!isNavbarBottom) {
                            padding(start = 12.dp, end = 12.dp, top = topInset + 6.dp)
                        }
                        // Height of the pill itself; the insets above are outer margin, so
                        // adding them here too would double-count them.
                        .height(if (isNavbarBottom) Dimensions.navigationBarHeight else 40.dp)
                        // Nearly opaque: the page now scrolls under the pill and text showing
                        // through it competed with the tab labels.
                        .glassSurface(shape = RoundedCornerShape(percent = 50), alpha = 0.96f)
                ) {
                    val scrollState = rememberScrollState()

                    if (UiType.ViMusic.isCurrent() && NavRoutes.home.isNotHere(navController))
                        BackButton().Draw()

                    // Collect tabs once per composition to adapt layout for many vs few tabs.
                    val transition = updateTransition(targetState = tabIndex, label = null)
                    val items = mutableListOf<Triple<Int, String, Int>>()
                    buttonsProvider?.invoke { index, text, iconId ->
                        items.add(Triple(index, text, iconId))
                    }
                    // With more than 5 tabs (e.g. the 7-tab search bar) labelled tabs no longer
                    // fit: a scrolling row left the last ones cut at the right edge. Those bars
                    // go icons-only instead, evenly spread so every tab is visible at once.
                    // The 5-tab main bar keeps the original labelled layout.
                    val isCompact = items.size > 5

                    // One indicator pill slides between the icon boxes. Positions are kept as root
                    // coordinates and compared with the container's, so they stay consistent while
                    // the whole bar slides in.
                    val itemOffsets = remember { mutableStateMapOf<Int, Offset>() }
                    val itemSizes = remember { mutableStateMapOf<Int, IntSize>() }
                    var containerOrigin by remember { mutableStateOf(Offset.Zero) }
                    val selectedInBar = items.any { it.first == tabIndex }
                    val indicatorTarget =
                        if (selectedInBar) itemOffsets[tabIndex]?.let { it - containerOrigin } else null
                    val indicatorOffset = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
                    var indicatorPlaced by remember { mutableStateOf(false) }
                    LaunchedEffect(indicatorTarget) {
                        if (indicatorTarget != null) {
                            if (!indicatorPlaced) {
                                indicatorOffset.snapTo(indicatorTarget)
                                indicatorPlaced = true
                            } else {
                                indicatorOffset.animateTo(
                                    indicatorTarget,
                                    spring(dampingRatio = 0.72f, stiffness = 380f)
                                )
                            }
                        }
                    }
                    val indicatorAlpha by animateFloatAsState(
                        targetValue = if (indicatorTarget != null && indicatorPlaced) 1f else 0f,
                        animationSpec = tween(durationMillis = 180),
                        label = "indicatorAlpha"
                    )
                    val indicatorSize = itemSizes[tabIndex]
                    val indicatorWidth = with(density) { (indicatorSize?.width?.toDp() ?: 52.dp) }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp)
                            .onGloballyPositioned { containerOrigin = it.positionInRoot() }
                    ) {
                    Box(
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    indicatorOffset.value.x.roundToInt(),
                                    indicatorOffset.value.y.roundToInt()
                                )
                            }
                            .size(width = indicatorWidth, height = 30.dp)
                            .graphicsLayer { alpha = indicatorAlpha }
                            .background(colorPalette().accent, RoundedCornerShape(percent = 50))
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items.forEach { (index, text, iconId) ->
                            val isSelected = tabIndex == index
                            // Yammbo Noir: the active tab is a solid white pill behind a black icon.
                            val iconColor by transition.animateColor(label = "") {
                                if (it == index) colorPalette().onAccent else colorPalette().textSecondary
                            }
                            val labelColor by transition.animateColor(label = "") {
                                if (it == index) colorPalette().accent else colorPalette().textSecondary
                            }
                            val iconScale by animateFloatAsState(
                                targetValue = 1f,
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                label = "iconScale"
                            )
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Top,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(onClick = { onTabChanged(index) })
                                    // Icons-only tabs still need a name for TalkBack; the label is not drawn
                                    .semantics(mergeDescendants = true) {
                                        contentDescription = text
                                        role = Role.Tab
                                        selected = isSelected
                                    }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .scale(iconScale)
                                        .size(width = 52.dp, height = 30.dp)
                                        .onGloballyPositioned {
                                            itemOffsets[index] = it.positionInRoot()
                                            itemSizes[index] = it.size
                                        }
                                ) {
                                    Button(iconId, iconColor, 0.dp, 20.dp).Draw()
                                }
                                if (!isCompact && !NavigationBarType.IconOnly.isCurrent()) {
                                    Spacer(modifier = Modifier.height(2.dp))
                                    androidx.compose.foundation.text.BasicText(
                                        text = text,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                        style = it.fast4x.riplay.utils.typography().xxs.copy(
                                            color = labelColor,
                                            fontSize = androidx.compose.ui.unit.TextUnit(10f, androidx.compose.ui.unit.TextUnitType.Sp),
                                            fontWeight = if (isSelected)
                                                androidx.compose.ui.text.font.FontWeight.SemiBold
                                            else
                                                androidx.compose.ui.text.font.FontWeight.Normal
                                        )
                                    )
                                }
                            }
                        }
                    }
                    }

                    if (UiType.ViMusic.isCurrent() && showSearchIconInNav())
                        SearchButton()

                    if (UiType.ViMusic.isCurrent())
                        SettingsButton().Draw()

                    if (UiType.ViMusic.isCurrent() && showStatsIconInNav())
                        StatsButton()
                }
            }
        }
    }
}

/*
// Shown when "Navigation bar position" is set to "top" or "bottom"
class HorizontalNavigationBar(
    val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
) : AbstractNavigationBar(navController, modifier) {

    private fun navButtonProperties(): Modifier {
        val padding: Dp = 4.dp
        val size: Dp = 24.dp
        val border: Shape = CircleShape

        return Modifier.padding(all = padding)
            .size(size)
            .clip(shape = border)
    }

    @Composable
    private fun addButton(button: Button, modifier: Modifier = Modifier) =
        buttonList.add {
            Box(modifier) { button.Draw() }
        }

    @SuppressLint("ComposableNaming")
    @Composable
    private fun addButton(index: Int, button: Button, modifier: Modifier = Modifier) =
        buttonList.add(index) {
            Box(modifier) { button.Draw() }
        }

    @Composable
    private fun bottomPadding(): Dp = 0.dp

    private fun topPadding(): Dp = 0.dp

    @Composable
    override fun add(buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit) {
        val transition = updateTransition(targetState = tabIndex, label = null)

        buttons { index, text, iconId ->

            val color by transition.animateColor(label = "") {
                if (it == index) colorPalette().text else colorPalette().textDisabled
            }

            val button: Button =
                if (NavigationBarType.IconOnly.isCurrent())
                    Button(iconId, color, 12.dp, 20.dp)
                else
                    TextIconButton(text, iconId, color, 0.dp, Dimensions.navigationRailIconOffset * 3)

            val contentModifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = { onTabChanged(index) })

            addButton(button, contentModifier)
        }
    }

    @Composable
    override fun BackButton(): NavigationButton {
        val button = super.BackButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SettingsButton(): NavigationButton {
        val button = super.SettingsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun StatsButton(): NavigationButton {
        val button = super.StatsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SearchButton(): NavigationButton {
        val button = super.SearchButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun Draw() {

        val isNavbarBottom = NavigationBarPosition.Bottom.isCurrent()
        val density = LocalDensity.current
        val bottomInset = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        val topInset = with(density) { WindowInsets.statusBars.getTop(density).toDp() }

        val contentPadding = if (isNavbarBottom) {
            PaddingValues(bottom = bottomInset)
        } else {
            PaddingValues(top = topInset)
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = if (NavigationBarPosition.Bottom.isCurrent()) Arrangement.Bottom else Arrangement.Top,
            modifier = modifier
                .fillMaxWidth()
                .background(colorPalette().background1)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .height( if (isNavbarBottom) Dimensions.navigationBarHeight + bottomInset else 40.dp + topInset)
                    .applyIf(isNavbarBottom) {
                        padding(contentPadding)
                    }
                    .clip(RoundedCornerShape(12.dp))
                    .background(colorPalette().background1)
            ) {
                val scrollState = rememberScrollState()

                if (UiType.ViMusic.isCurrent() && NavRoutes.home.isNotHere(navController))
                    BackButton().Draw()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Transparent)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxSize()
                            .horizontalScroll(scrollState),
                        content = { buttonList().forEach { it() } }
                    )
                }

                if (UiType.ViMusic.isCurrent() && showSearchIconInNav())
                    SearchButton()

                if (UiType.ViMusic.isCurrent())
                    SettingsButton().Draw()

                if (UiType.ViMusic.isCurrent() && showStatsIconInNav())
                    StatsButton()
            }
        }
    }
}
*/
/*
// Shown when "Navigation bar position" is set to "top" or "bottom"
class HorizontalNavigationBar(
    val tabIndex: Int,
    val onTabChanged: (Int) -> Unit,
    navController: NavController,
    modifier: Modifier = Modifier
): AbstractNavigationBar( navController, modifier ) {

    private fun navButtonProperties(): Modifier {
        val padding: Dp = 4.dp
        val size: Dp = 24.dp
        val border: Shape = CircleShape

        return Modifier.padding( all = padding )
                       .size( size )
                       .clip( shape = border )
    }

    @Composable
    private fun addButton(button: Button, modifier: Modifier = Modifier ) =
        // buttonList() duplicates button instead of updating them.
        // Do NOT use it
        buttonList.add {
            Box( modifier ) { button.Draw() }
        }

    @SuppressLint("ComposableNaming")
    @Composable
    private fun addButton(index: Int, button: Button, modifier: Modifier = Modifier ) =
        // buttonList() duplicates button instead of updating them
        // Do NOT use it
        buttonList.add( index ) {
            Box( modifier ) { button.Draw() }
        }

    @Composable
    private fun bottomPadding(): Dp {
        return 0.dp
//        return if ( NavigationBarPosition.Bottom.isCurrent() )
//            with( LocalDensity.current ) {
//                WindowInsets.systemBars.getBottom( this ).toDp()
//            }
//        else
//            5.dp
    }

    private fun topPadding(): Dp = 0.dp

    @Composable
    override fun add(buttons: @Composable (@Composable (Int, String, Int) -> Unit) -> Unit) {
        val transition = updateTransition(targetState = tabIndex, label = null)

        buttons { index, text, iconId ->

            val color by transition.animateColor(label = "") {
                if (it == index) colorPalette().text else colorPalette().textDisabled
            }

            val button: Button =
                if ( NavigationBarType.IconOnly.isCurrent() )
                    Button( iconId, color, 12.dp, 20.dp )
                else
                    TextIconButton( text, iconId, color, 0.dp, Dimensions.navigationRailIconOffset * 3 )

            val contentModifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = { onTabChanged(index) })

            addButton( button, contentModifier )
        }
    }

    @Composable
    override fun BackButton(): NavigationButton {
        val button = super.BackButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SettingsButton(): NavigationButton {
        val button = super.SettingsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun StatsButton(): NavigationButton {
        val button = super.StatsButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun SearchButton(): NavigationButton {
        val button = super.SearchButton()
        button.modifier = this.navButtonProperties()
        return button
    }

    @Composable
    override fun Draw() {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
            modifier = modifier.padding( top = topPadding(), bottom = bottomPadding() )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceAround,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Dimensions.navigationBarHeight + WindowInsets.systemBars.getBottom(LocalDensity.current).dp)
                    //.padding(bottom = 5.dp)
            ) {

                val scrollState = rememberScrollState()
                val roundedCornerShape =
                    if ( NavigationBarPosition.Bottom.isCurrent() )
                        RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
                    else
                        RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp)

                // Settings button only visible when
                // UI is not RiPlay and current location isn't home screen
                if( UiType.ViMusic.isCurrent() && NavRoutes.home.isNotHere( navController ) )
                    BackButton().Draw()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(roundedCornerShape)
                        .background(colorPalette().background1)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier
                            //.padding(bottom = 10.dp)
                            .padding(bottom = WindowInsets.systemBars.getBottom(LocalDensity.current).dp)
                            .fillMaxWidth()
                            .fillMaxSize()
                            .horizontalScroll(scrollState),
                        content = { buttonList().forEach { it() } }
                    )
                }

                // Search button only visible when
                // UI is not RiPlay and must be explicitly turned on
                if( UiType.ViMusic.isCurrent() && showSearchIconInNav() )
                    SearchButton()

                // Settings button only visible when
                // UI is not RiPlay
                if( UiType.ViMusic.isCurrent() )
                    SettingsButton().Draw()

                // Statistics button only visible when
                // UI is not RiPlay and must be explicitly turned on
                if( UiType.ViMusic.isCurrent() && showStatsIconInNav() )
                    StatsButton()
            }
        }
    }
}
*/