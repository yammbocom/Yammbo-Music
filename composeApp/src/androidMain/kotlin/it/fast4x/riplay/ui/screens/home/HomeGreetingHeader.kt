package it.fast4x.riplay.ui.screens.home

import android.annotation.SuppressLint
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.mutableStateOf
import it.fast4x.riplay.commonutils.thumbnail
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.R
import it.fast4x.environment.Environment
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.data.models.Song
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.extensions.persist.persistList
import it.fast4x.riplay.extensions.yammboapi.YammboAuthManager
import it.fast4x.riplay.ui.components.NoirContentMax
import it.fast4x.riplay.ui.components.RevealImage
import it.fast4x.riplay.ui.components.noirContentWidth
import it.fast4x.riplay.ui.components.rememberNoirLayout
import it.fast4x.riplay.ui.components.pressable
import it.fast4x.riplay.ui.screens.settings.isYtLoggedIn
import it.fast4x.riplay.utils.ytAccountName
import it.fast4x.riplay.ui.styling.secondary
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.asMediaItem
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.LOCAL_KEY_PREFIX
import it.fast4x.riplay.utils.forcePlayAtIndex
import it.fast4x.riplay.utils.isRadio
import it.fast4x.riplay.utils.typography
import it.fast4x.riplay.ui.components.themed.NoirEyebrow
import it.fast4x.riplay.ui.styling.tight
import androidx.compose.ui.text.font.FontWeight
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.Calendar

/** Rows x columns of the "jump back in" grid. */
private const val JUMP_BACK_IN_ITEMS = 6

/**
 * Most recent first, but varied: one song per cover (album) and at most two per artist, so an
 * album played in order doesn't fill the grid with the same art. Topped up in order if short.
 */
private fun List<Song>.variedRecent(count: Int): List<Song> {
    val picked = mutableListOf<Song>()
    val covers = HashSet<String>()
    val perArtist = HashMap<String, Int>()
    for (song in this) {
        if (picked.size == count) break
        val cover = song.thumbnailUrl ?: song.id
        val artist = song.artistsText.orEmpty().lowercase()
        if (cover in covers || (perArtist[artist] ?: 0) >= 2) continue
        picked += song
        covers += cover
        perArtist[artist] = (perArtist[artist] ?: 0) + 1
    }
    if (picked.size < count) picked += filter { it !in picked }.take(count - picked.size)
    return picked
}

// A YouTube video id is exactly 11 chars; anything else that is not a local file (station
// playlists, mixes) is not a song to show in the Home song sections.
private val homeVideoIdRegex = Regex("^[A-Za-z0-9_-]{11}$")

// Safety net for videos stored without the video flag (e.g. played from a related list, which
// does not carry it): "(Official Lyric Video)", "Official Music Video", "Lyric Video".
private val homeVideoTitleRegex =
    Regex("\\b(official\\s+(lyric\\s+|music\\s+)?video|lyric\\s+video|music\\s+video)\\b", RegexOption.IGNORE_CASE)

private fun isHomeSongId(id: String): Boolean =
    id.startsWith(LOCAL_KEY_PREFIX) || homeVideoIdRegex.matches(id)

/**
 * True for a real song: not a video (the isAudioOnly flag behind the film badge), not a podcast
 * episode, not a live station. Used by every song section of Home.
 */
internal val Song.isHomeSong: Boolean
    get() = !isVideo && isPodcast == 0 && !isRadio && isHomeSongId(id) &&
            !homeVideoTitleRegex.containsMatchIn(title)

/**
 * Same rule for songs coming from the network (related page). The related list does not set
 * isAudioOnly, so the YTM video type (OMV / UGC) and the wide thumbnail are the real signals.
 */
internal val Environment.SongItem.isHomeSong: Boolean
    get() {
        val videoType = info?.endpoint?.watchEndpointMusicSupportedConfigs?.watchEndpointMusicConfig?.musicVideoType
        val thumb = thumbnail
        // Only when YTM gives no type: videos have 16:9 art, songs square art.
        val wideArt = videoType == null && (thumb?.width ?: 0) > (thumb?.height ?: 0)
        return isAudioOnly && !isOfficialMusicVideo && !isUserGeneratedContent && !wideArt &&
                isHomeSongId(key) && !homeVideoTitleRegex.containsMatchIn(info?.name.orEmpty())
    }

/** Related page with only real songs in its song list (other lists untouched). */
internal fun Environment.RelatedPage.onlySongs(): Environment.RelatedPage =
    copy(songs = songs?.filter { it.isHomeSong })

/**
 * Entry of a YouTube Music home-feed shelf that Home shows: no music videos (video items or
 * OMV/UGC songs). Albums, artists and playlists stay.
 */
internal val Environment.Item.isHomeShelfItem: Boolean
    get() = when (this) {
        is Environment.VideoItem -> false
        is Environment.SongItem -> isHomeSong
        else -> true
    }

/**
 * Home header: avatar, time-of-day greeting and the account name, with the
 * screen's actions as circular buttons on the right. Replaces the plain
 * greeting line so Home opens on something that identifies the user.
 */
@Composable
fun HomeGreetingHeader(
    navController: NavController,
    onSettingsClick: () -> Unit,
    extraActions: @Composable RowScope.() -> Unit = {},
    titleModifier: Modifier = Modifier
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

    // Yammbo Noir: "WEDNESDAY · 29 OCT" in mono over a big greeting with the name.
    val dateLabel = remember {
        SimpleDateFormat("EEEE · d MMM", Locale.getDefault()).format(Calendar.getInstance().time)
            .replace(".", "")
            .uppercase()
    }

    // The tools menu (equalizer, history, statistics, settings…) hangs off the profile here.
    // It used to live in the top bar, where it competed with the search icon.
    var menuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .noirContentWidth(NoirContentMax.Grid)
            .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            NoirEyebrow(text = dateLabel, modifier = Modifier.weight(1f))

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
            Spacer(modifier = Modifier.width(8.dp))
            AccountAvatarButton(
                navController = navController,
                menuExpanded = menuExpanded,
                onMenuExpandedChange = { menuExpanded = it }
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        BasicText(
            text = if (userName.isNotEmpty()) "$greeting,\n$userName" else greeting,
            style = typography().xxl.tight.copy(
                color = colors.text,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                lineHeight = 38.sp
            ),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = titleModifier.clickable { menuExpanded = true }
        )
    }
}

/**
 * The signed-in account as a solid circle (photo, or the name's initial). Tap opens the Account
 * screen; long-press opens the tools menu. Shared by the Home greeting, the other tabs' headers
 * and the top bar. The greeting text opens the same menu, so the expanded state can be hoisted.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountAvatarButton(
    navController: NavController,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    menuExpanded: Boolean? = null,
    onMenuExpandedChange: ((Boolean) -> Unit)? = null
) {
    val colors = colorPalette()
    val context = LocalContext.current
    val authManager = remember { YammboAuthManager(context) }
    val userName = authManager.getUserName()
        .ifEmpty { if (isYtLoggedIn()) ytAccountName().orEmpty() else "" }
    val avatarUrl = authManager.getUserAvatar()

    var localExpanded by remember { mutableStateOf(false) }
    val expanded = menuExpanded ?: localExpanded
    val setExpanded: (Boolean) -> Unit = onMenuExpandedChange ?: { localExpanded = it }

    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(colors.text)
            .combinedClickable(
                onClick = { navController.navigate(NavRoutes.myAccount.name) },
                onLongClick = { setExpanded(true) }
            ),
        contentAlignment = Alignment.Center
    ) {
        HamburgerMenu(
            expanded = expanded,
            onItemClick = { route ->
                setExpanded(false)
                navController.navigate(route.name)
            },
            onDismissRequest = { setExpanded(false) }
        )
        if (avatarUrl.isNotEmpty()) {
            RevealImage(
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
                style = typography().m.semiBold.copy(color = colors.background0)
            )
        }
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
            .size(40.dp)
            .clip(CircleShape)
            .pressable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(iconId),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(colors.text),
            modifier = Modifier.size(22.dp)
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
        // SQL already drops podcasts, stations and flagged videos; the rest of the rule runs here
        // over a wider window so the grid still fills up after filtering.
        Database.lastPlayedSongsOnly(JUMP_BACK_IN_ITEMS * 8).collect { list ->
            recent = list.filter { it.isHomeSong }.distinctBy { it.id }.variedRecent(JUMP_BACK_IN_ITEMS)
        }
    }

    if (recent.isEmpty()) return

    // 2 columns on phones (same as before); more on tablets.
    val cols = rememberNoirLayout().columns(minItem = 170.dp, spacing = 10.dp)

    Column(modifier = Modifier.noirContentWidth(NoirContentMax.Grid)) {
        SectionTitleWithRule(title = stringResource(R.string.home_jump_back_in))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            recent.chunked(cols).forEach { row ->
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
                    // Keep the last cards at cell width instead of stretching them.
                    repeat(cols - row.size) { Spacer(modifier = Modifier.weight(1f)) }
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
            .clip(RoundedCornerShape(12.dp))
            .background(colors.background1)
            .pressable(onClick = onClick)
            .padding(end = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(topStart = 12.dp, bottomStart = 12.dp))
                .background(colors.background2),
            contentAlignment = Alignment.Center
        ) {
            if (!song.thumbnailUrl.isNullOrEmpty()) {
                RevealImage(
                    model = song.thumbnailUrl.thumbnail(180),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
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
                style = typography().xs.semiBold.copy(color = colors.text, fontSize = 15.sp),
                maxLines = 2,
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
            .padding(top = 24.dp, bottom = 12.dp)
    ) {
        BasicText(
            text = title,
            style = typography().xl.tight.copy(color = colors.text, fontWeight = FontWeight.Bold, fontSize = 22.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
