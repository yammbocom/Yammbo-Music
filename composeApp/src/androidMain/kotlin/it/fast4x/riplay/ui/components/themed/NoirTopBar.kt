package it.fast4x.riplay.ui.components.themed

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.ui.components.TopBarTitle
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

/**
 * Yammbo Noir minimal top row: status bar inset, 56dp, optional back circle, optional title and
 * trailing [actions]. It replaces the legacy logo bar on pages that draw their own Noir header.
 *
 * With a null [title] it shows the page's collapsed large title (published through
 * [TopBarTitle] by CollapsingTitleEffect), fading in as the big title scrolls away.
 */
@Composable
fun NoirTopBar(
    onBack: (() -> Unit)?,
    title: String? = null,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {}
) {
    val colors = colorPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .background(colors.background0)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
    ) {
        if (onBack != null) {
            val backDescription = stringResource(R.string.back)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .padding(start = 12.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(colors.text.copy(alpha = 0.08f))
                    .clickable(onClickLabel = backDescription, role = Role.Button, onClick = onBack)
            ) {
                Image(
                    painter = painterResource(R.drawable.chevron_back),
                    contentDescription = backDescription,
                    colorFilter = ColorFilter.tint(colors.text),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        val titleStyle = typography().xs.copy(
            color = colors.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 17.sp
        )
        Box(
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier
                .weight(1f)
                .padding(start = if (onBack != null) 12.dp else 20.dp, end = 8.dp)
        ) {
            if (title != null) {
                BasicText(
                    text = title,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                // Alpha is read in the layer so scrolling does not recompose the bar.
                BasicText(
                    text = TopBarTitle.text,
                    style = titleStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.graphicsLayer { alpha = TopBarTitle.progress }
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End,
            modifier = Modifier.padding(end = 8.dp),
            content = actions
        )
    }
}

/**
 * What a gear icon button does: open the account page, which links to the settings. When the
 * account page is already open the gear goes straight to the settings instead.
 */
fun NavController.openAccountOrSettings() {
    val route = currentBackStackEntry?.destination?.route
    if (route == NavRoutes.myAccount.name)
        navigate(NavRoutes.settings.name) { launchSingleTop = true }
    else
        navigate(NavRoutes.myAccount.name) { launchSingleTop = true }
}

/** Back arrow of [NoirTopBar]: pops once, ignoring taps while a transition is running. */
fun NavController.noirBack() {
    if (currentBackStackEntry?.lifecycle?.currentState == androidx.lifecycle.Lifecycle.State.RESUMED)
        popBackStack()
}
