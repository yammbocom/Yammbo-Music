package it.fast4x.riplay.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
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
