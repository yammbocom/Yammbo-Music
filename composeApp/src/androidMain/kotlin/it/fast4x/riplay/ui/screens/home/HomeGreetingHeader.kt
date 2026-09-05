package it.fast4x.riplay.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateOf
import it.fast4x.riplay.ui.components.navigation.header.HamburgerMenu
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.persist.persistList
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.screens.settings.isYtLoggedIn
import it.fast4x.riplay.utils.ytAccountName
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.typography
import java.text.SimpleDateFormat
import java.util.Calendar

/** Rows x columns of the "jump back in" grid. */
private const val JUMP_BACK_IN_ITEMS = 4

/**
 * Home header: avatar, time-of-day greeting and the account name, with the
 * screen's actions as circular buttons on the right. Replaces the plain
 * greeting line so Home opens on something that identifies the user.
 */
@Composable
fun HomeGreetingHeader(
    navController: NavController,
    onSettingsClick: () -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {}
) {
    val colors = colorPalette()
    val context = LocalContext.current
    val authManager = remember { YammboAuthManager(context) }

    // Yammbo account first; fall back to the YouTube name so the header keeps
    // showing who is signed in when only the YT session exists.
    val userName = authManager.getUserName()
        .ifEmpty { if (isYtLoggedIn()) ytAccountName().orEmpty() else "" }
    val avatarUrl = authManager.getUserAvatar()

    val hour = remember {
        val date = Calendar.getInstance().time
        @SuppressLint("SimpleDateFormat")
        val formatter = SimpleDateFormat("HH")
        formatter.format(date).toInt()
    }

    val greeting = when (hour) {
        in 6..12 -> stringResource(R.string.good_morning)
        in 13..17 -> stringResource(R.string.good_afternoon)
        in 18..23 -> stringResource(R.string.good_evening)
        else -> stringResource(R.string.good_night)
    }

    // The tools menu (equalizer, history, statistics, settings…) hangs off the profile here.
    // It used to live in the top bar, where it competed with the search icon.
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(colors.background2)
                .clickable { menuExpanded = true },
            contentAlignment = Alignment.Center
        ) {
            HamburgerMenu(
                expanded = menuExpanded,
                onItemClick = { route ->
                    menuExpanded = false
                    navController.navigate(route.name)
                },
                onDismissRequest = { menuExpanded = false }
            )
            if (avatarUrl.isNotEmpty()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                )
            } else {
                BasicText(
                    text = userName.take(1).uppercase().ifEmpty { "?" },
                    style = typography().m.semiBold.copy(color = colors.text)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .clickable { menuExpanded = true }
        ) {
            BasicText(
                text = greeting.uppercase(),
                style = typography().xxs.copy(
                    color = colors.textSecondary,
                    letterSpacing = 1.2.sp
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (userName.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = userName,
                    style = typography().l.semiBold.copy(
                        color = colors.text,
                        fontSize = 20.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        extraActions()

        HomeCircleButton(
            iconId = R.drawable.history,
            contentDescription = stringResource(R.string.history),
            onClick = { navController.navigate(NavRoutes.history.name) }
        )
        Spacer(modifier = Modifier.width(8.dp))
        HomeCircleButton(
            iconId = R.drawable.settings,
            contentDescription = stringResource(R.string.settings),
            onClick = onSettingsClick
        )
    }
}

@Composable
fun HomeCircleButton(
    iconId: Int,
    contentDescription: String,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(colors.background1)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(colors.text),
            modifier = Modifier.size(17.dp)
        )
    }
}

/**
 * "Jump back in": the last songs played, as a compact 2-column grid of
 * cover + title rows. Renders nothing until there is history, so a fresh
 * install doesn't open on an empty box.
 */
@UnstableApi
@Composable
fun JumpBackInSection() {
    val binder = LocalPlayerServiceBinder.current
    var recent by persistList<Song>("home/jumpBackIn")

    LaunchedEffect(Unit) {
        Database.lastPlayed(JUMP_BACK_IN_ITEMS * 4).collect { list ->
            recent = list.distinctBy { it.id }.take(JUMP_BACK_IN_ITEMS)
        }
    }

    if (recent.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        SectionTitleWithRule(title = stringResource(R.string.home_jump_back_in))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            recent.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { song ->
                        JumpBackInCard(
                            song = song,
                            modifier = Modifier.weight(1f)
                        ) {
                            binder?.stopRadio()
                            binder?.player?.forcePlayAtIndex(
                                recent.map(Song::asMediaItem),
                                recent.indexOf(song)
                            )
                        }
                    }
                    // Keep the last odd card at half width instead of stretching it.
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun JumpBackInCard(
    song: Song,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colors = colorPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.background1)
            .pressable(onClick = onClick)
            .padding(end = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.background2),
            contentAlignment = Alignment.Center
        ) {
            if (!song.thumbnailUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = song.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Image(
                    painter = painterResource(R.drawable.musical_notes),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(colors.textDisabled),
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            BasicText(
                text = song.cleanTitle(),
                style = typography().xs.semiBold.copy(color = colors.text),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            song.artistsText?.takeIf { it.isNotEmpty() }?.let { artists ->
                Spacer(modifier = Modifier.height(2.dp))
                BasicText(
                    text = artists,
                    style = typography().xxs.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Section title with a short accent rule. In the reference the same emphasis is
 * carried by a coloured bar; here it is the accent (white on dark, black on
 * light), which is the only "colour" the brand allows.
 */
@Composable
fun SectionTitleWithRule(title: String) {
    val colors = colorPalette()
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 22.dp, bottom = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(18.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent)
        )
        Spacer(modifier = Modifier.width(9.dp))
        BasicText(
            text = title,
            style = typography().m.semiBold.copy(color = colors.text),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
