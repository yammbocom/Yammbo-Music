package it.fast4x.riplay.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import coil3.compose.AsyncImage
import it.fast4x.riplay.ui.components.themed.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.yambo.music.R
import it.fast4x.riplay.enums.HomePagetype
import it.fast4x.riplay.enums.NavigationBarPosition
import it.fast4x.riplay.extensions.preferences.homePageTypeKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.extensions.preferences.showChartsKey
import it.fast4x.riplay.extensions.preferences.showListenerLevelsKey
import it.fast4x.riplay.extensions.preferences.showMonthlyPlaylistInQuickPicksKey
import it.fast4x.riplay.extensions.preferences.showMoodsAndGenresKey
import it.fast4x.riplay.extensions.preferences.showNewAlbumsArtistsKey
import it.fast4x.riplay.extensions.preferences.showNewAlbumsKey
import it.fast4x.riplay.extensions.preferences.showLiveRadioKey
import it.fast4x.riplay.extensions.preferences.showPlaylistMightLikeKey
import it.fast4x.riplay.extensions.preferences.showRelatedAlbumsKey
import it.fast4x.riplay.extensions.preferences.showSimilarArtistsKey
import it.fast4x.riplay.extensions.preferences.showTipsKey
import it.fast4x.riplay.ui.styling.Dimensions
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography

@ExperimentalAnimationApi
@UnstableApi
@Composable
fun HomeSettings(navController: androidx.navigation.NavController? = null) {
    var showListenerLevels by rememberPreference(showListenerLevelsKey, true)
    var showTips by rememberPreference(showTipsKey, true)
    var showRelatedAlbums by rememberPreference(showRelatedAlbumsKey, true)
    var showSimilarArtists by rememberPreference(showSimilarArtistsKey, true)
    var showNewAlbumsArtists by rememberPreference(showNewAlbumsArtistsKey, true)
    var showNewAlbums by rememberPreference(showNewAlbumsKey, true)
    var showLiveRadio by rememberPreference(showLiveRadioKey, true)
    var showPlaylistMightLike by rememberPreference(showPlaylistMightLikeKey, true)
    var showMoodsAndGenres by rememberPreference(showMoodsAndGenresKey, true)
    var showMonthlyPlaylistInQuickPicks by rememberPreference(showMonthlyPlaylistInQuickPicksKey, true)
    var showCharts by rememberPreference(showChartsKey, true)
    var homePageType by rememberPreference(homePageTypeKey, HomePagetype.Classic)

    Column(
        modifier = Modifier
            .background(colorPalette().background0)
            .fillMaxHeight()
            .fillMaxWidth(
                if (NavigationBarPosition.Right.isCurrent())
                    Dimensions.contentWidthRightBar
                else
                    1f
            )
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(modifier = Modifier.height(40.dp))

        // Homepage Type
        SettingsCard(title = stringResource(R.string.home)) {
            EnumValueSelectorSettingsEntry(
                offline = false,
                title = stringResource(R.string.homepage_type),
                selectedValue = homePageType,
                onValueSelected = { homePageType = it },
                valueText = {
                    when (it) {
                        HomePagetype.Classic -> stringResource(R.string.homepage_classic)
                        HomePagetype.Extended -> stringResource(R.string.homepage_extended)
                    }
                }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sections visibility
        SettingsCard(title = stringResource(R.string.show)) {
            SwitchSettingEntry(
                offline = false,
                title = stringResource(R.string.listener_levels),
                text = "",
                isChecked = showListenerLevels,
                onCheckedChange = { showListenerLevels = it }
            )
            SwitchSettingEntry(
                offline = false,
                title = stringResource(R.string.quick_picks),
                text = "",
                isChecked = showTips,
                onCheckedChange = { showTips = it }
            )
            SwitchSettingEntry(
                offline = false,
                title = stringResource(R.string.new_albums_of_your_artists),
                text = "",
                isChecked = showNewAlbumsArtists,
                onCheckedChange = { showNewAlbumsArtists = it }
            )
            SwitchSettingEntry(
                title = stringResource(R.string.new_albums),
                text = "",
                isChecked = showNewAlbums,
                onCheckedChange = { showNewAlbums = it }
            )
            SwitchSettingEntry(
                title = stringResource(R.string.live_radio),
                text = "",
                isChecked = showLiveRadio,
                onCheckedChange = { showLiveRadio = it }
            )
            SwitchSettingEntry(
                offline = false,
                title = stringResource(R.string.moods_and_genres),
                text = "",
                isChecked = showMoodsAndGenres,
                onCheckedChange = { showMoodsAndGenres = it }
            )
        }

        AnimatedVisibility(
            visible = homePageType == HomePagetype.Extended,
        ) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))

                SettingsCard(title = "Extended") {
                    SwitchSettingEntry(
                        offline = false,
                        title = stringResource(R.string.charts),
                        text = "",
                        isChecked = showCharts,
                        onCheckedChange = { showCharts = it }
                    )
                    SwitchSettingEntry(
                        offline = false,
                        title = stringResource(R.string.related_albums),
                        text = "",
                        isChecked = showRelatedAlbums,
                        onCheckedChange = { showRelatedAlbums = it }
                    )
                    SwitchSettingEntry(
                        offline = false,
                        title = stringResource(R.string.similar_artists),
                        text = "",
                        isChecked = showSimilarArtists,
                        onCheckedChange = { showSimilarArtists = it }
                    )
                    SwitchSettingEntry(
                        offline = false,
                        title = stringResource(R.string.playlists_you_might_like),
                        text = "",
                        isChecked = showPlaylistMightLike,
                        onCheckedChange = { showPlaylistMightLike = it }
                    )
                    SwitchSettingEntry(
                        offline = false,
                        title = stringResource(R.string.monthly_playlists),
                        text = "",
                        isChecked = showMonthlyPlaylistInQuickPicks,
                        onCheckedChange = { showMonthlyPlaylistInQuickPicks = it }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(Dimensions.bottomSpacer))
    }
}

/**
 * One connected service.
 *
 * The old cards said only "Enable X" behind a toggle, so whether an account was actually
 * linked - and which one - took a tap each to find out. This one carries the badge, the
 * name, the live status and the switch on a single line, and keeps the actions folded
 * underneath.
 */
@Composable
internal fun AccountCard(
    title: String,
    @DrawableRes icon: Int,
    connected: Boolean,
    statusText: String,
    modifier: Modifier = Modifier,
    avatarUrl: String? = null,
    switchChecked: Boolean? = null,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    switchEnabled: Boolean = true,
    content: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colorPalette().background1)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(colorPalette().background2),
                contentAlignment = Alignment.Center,
            ) {
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                } else {
                    Image(
                        painter = painterResource(icon),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(colorPalette().text),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                BasicText(
                    text = title,
                    style = typography().s.copy(
                        fontWeight = FontWeight.SemiBold,
                        color = colorPalette().text,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Filled while the account is linked, hollow while it is not: the state
                    // has to read from across the room without turning the app colourful.
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(
                                if (connected) colorPalette().text else colorPalette().textDisabled
                            )
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    BasicText(
                        text = statusText,
                        style = typography().xxs.copy(color = colorPalette().textSecondary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (switchChecked != null && onSwitchChange != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = switchEnabled) { onSwitchChange(!switchChecked) }
                        .padding(start = 10.dp, top = 6.dp, bottom = 6.dp)
                ) {
                    Switch(isChecked = switchChecked)
                }
            }
        }

        if (content != null) {
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colorPalette().background2)
            )
            content()
        }
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colorPalette().background1)
            .padding(16.dp)
    ) {
        BasicText(
            text = title.uppercase(),
            style = typography().xxs.copy(
                fontWeight = FontWeight.SemiBold,
                color = colorPalette().textSecondary
            )
        )
        Spacer(modifier = Modifier.height(8.dp))
        content()
    }
}
