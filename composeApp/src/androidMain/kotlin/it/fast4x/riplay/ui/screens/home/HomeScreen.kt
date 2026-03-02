package it.fast4x.riplay.ui.screens.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.lifecycle.Lifecycle
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.yambo.music.BuildConfig
import it.fast4x.riplay.commonutils.LOCAL_KEY_PREFIX
import it.fast4x.riplay.LocalPlayerSheetState
import com.yambo.music.R
import it.fast4x.riplay.data.models.toUiChip
import it.fast4x.riplay.enums.CheckUpdateState
import it.fast4x.riplay.enums.HomeScreenTabs
import it.fast4x.riplay.enums.NavRoutes
import it.fast4x.riplay.data.models.toUiMood
import it.fast4x.riplay.enums.HomePagetype
import it.fast4x.riplay.ui.components.themed.ConfirmationDialog
import it.fast4x.riplay.ui.components.themed.SmartMessage
import it.fast4x.riplay.utils.CheckAvailableNewVersion
import it.fast4x.riplay.extensions.preferences.checkUpdateStateKey
import it.fast4x.riplay.extensions.preferences.enableMusicIdentifierKey
import it.fast4x.riplay.extensions.preferences.getEnum
import it.fast4x.riplay.extensions.preferences.homePageTypeKey
import it.fast4x.riplay.extensions.preferences.homeScreenTabIndexKey
import it.fast4x.riplay.extensions.preferences.indexNavigationTabKey
import it.fast4x.riplay.extensions.preferences.preferences
import it.fast4x.riplay.extensions.preferences.rememberObservedPreference
import it.fast4x.riplay.extensions.preferences.rememberPreference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import it.fast4x.riplay.ui.components.ScreenContainer
import it.fast4x.riplay.ui.screens.home.homepages.HomePage
import it.fast4x.riplay.ui.screens.home.homepages.HomePageExtended
import kotlin.system.exitProcess


@OptIn(ExperimentalMaterial3Api::class)
@ExperimentalMaterialApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun HomeScreen(
    navController: NavController,
    onPlaylistUrl: (String) -> Unit,
    miniPlayer: @Composable () -> Unit = {},
    openTabFromShortcut: Int
) {

    var showNewversionDialog by remember {
        mutableStateOf(true)
    }

    var checkUpdateState by rememberPreference(checkUpdateStateKey, CheckUpdateState.Disabled)

    val saveableStateHolder = rememberSaveableStateHolder()

    val preferences = LocalContext.current.preferences
    //val enableQuickPicksPage by rememberPreference(enableQuickPicksPageKey, true)

    val openTabFromShortcut1 by remember{ mutableIntStateOf(openTabFromShortcut) }

    var initialtabIndex =
            when (openTabFromShortcut1) {
            -1 -> try {
                when (preferences.getEnum(indexNavigationTabKey, HomeScreenTabs.Default)) {
                    HomeScreenTabs.Default -> HomeScreenTabs.Inicio.index
                    else -> preferences.getEnum(indexNavigationTabKey, HomeScreenTabs.Inicio).index
                }
            } catch (_: Exception) {
                HomeScreenTabs.Inicio.index
            }
            else -> openTabFromShortcut1
        }

    var (tabIndex, onTabChanged) = rememberPreference(homeScreenTabIndexKey, initialtabIndex)

    val isEnabledMusicIdentifier by rememberPreference(
        enableMusicIdentifierKey,
        false
    )

    val homePageType by rememberObservedPreference(homePageTypeKey, HomePagetype.Classic)

    if (tabIndex == -2 || tabIndex == 3) {
        onTabChanged(0)
        navController.navigate(NavRoutes.search.name)
    }
    if (tabIndex == -3) {
        onTabChanged(0)
        if (isEnabledMusicIdentifier)
            navController.navigate(NavRoutes.musicIdentifier.name)
        else {
            navController.navigate(NavRoutes.home.name)
            SmartMessage("Music Identifier is disabled", context = LocalContext.current)
        }
    }

    ScreenContainer(
        navController,
        tabIndex,
        onTabChanged,
        miniPlayer,
        navBarContent = { Item ->
            Item(0, stringResource(R.string.home), R.drawable.home)
            Item(1, stringResource(R.string.top_50), R.drawable.trending)
            Item(2, stringResource(R.string.my_music), R.drawable.musical_notes)
            Item(3, stringResource(R.string.search), R.drawable.search)
            Item(4, stringResource(R.string.my_account), R.drawable.person)
        }
    ) { currentTabIndex ->
        saveableStateHolder.SaveableStateProvider(key = currentTabIndex) {
            when (currentTabIndex) {
                0 -> when(homePageType) {
                    HomePagetype.Extended -> HomePageExtended(
                        onAlbumClick = {
                            navController.navigate(route = "${NavRoutes.album.name}/$it")
                        },
                        onArtistClick = {
                            navController.navigate(route = "${NavRoutes.artist.name}/$it")
                        },
                        onPlaylistClick = {
                            navController.navigate(route = "${NavRoutes.playlist.name}/$it")
                        },
                        onSearchClick = {
                            navController.navigate(NavRoutes.search.name)
                        },
                        onMoodAndGenresClick = { mood ->
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                "mood",
                                mood.toUiMood()
                            )
                            navController.navigate(NavRoutes.mood.name)
                        },
                        onChipClick = { chip ->
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                "chip",
                                chip.toUiChip()
                            )
                            navController.navigate(route = NavRoutes.chip.name)
                        },
                        onSettingsClick = {
                            navController.navigate(NavRoutes.settings.name)
                        },
                        navController = navController
                    )

                    HomePagetype.Classic -> HomePage(
                        onAlbumClick = {
                            navController.navigate(route = "${NavRoutes.album.name}/$it")
                        },
                        onArtistClick = {
                            navController.navigate(route = "${NavRoutes.artist.name}/$it")
                        },
                        onPlaylistClick = {
                            navController.navigate(route = "${NavRoutes.playlist.name}/$it")
                        },
                        onSearchClick = {
                            navController.navigate(NavRoutes.search.name)
                        },
                        onMoodAndGenresClick = { mood ->
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                "mood",
                                mood.toUiMood()
                            )
                            navController.navigate(NavRoutes.mood.name)
                        },
                        onChipClick = { chip ->
                            navController.currentBackStackEntry?.savedStateHandle?.set(
                                "chip",
                                chip.toUiChip()
                            )
                            navController.navigate(route = NavRoutes.chip.name)
                        },
                        onSettingsClick = {
                            navController.navigate(NavRoutes.settings.name)
                        },
                        navController = navController
                    )

                }

                1 -> Top50Tab(
                    navController = navController
                )

                2 -> MyMusicTab(
                    onSongsClick = { onTabChanged(10) },
                    onArtistsClick = { onTabChanged(11) },
                    onAlbumsClick = { onTabChanged(12) },
                    onPlaylistsClick = { onTabChanged(13) }
                )

                // 3 = Search, handled above (navigates to search screen)

                4 -> MyAccountTab(
                    navController = navController
                )

                // Sub-screens from MyMusicTab
                10 -> HomeSongs(
                    navController = navController,
                    onSearchClick = {
                        navController.navigate(NavRoutes.search.name)
                    },
                    onSettingsClick = {
                        navController.navigate(NavRoutes.settings.name)
                    }
                )

                11 -> HomeArtists(
                    navController = navController,
                    onArtistClick = {
                        if (!it.id.startsWith(LOCAL_KEY_PREFIX))
                            navController.navigate(route = "${NavRoutes.artist.name}/${it.id}")
                        else navController.navigate(route = "${NavRoutes.onDeviceArtist.name}/${it.id}")
                    },
                    onSearchClick = {
                        navController.navigate(NavRoutes.search.name)
                    },
                    onSettingsClick = {
                        navController.navigate(NavRoutes.settings.name)
                    }
                )

                12 -> HomeAlbums(
                    navController = navController,
                    onAlbumClick = {
                        if (!it.id.startsWith(LOCAL_KEY_PREFIX))
                            navController.navigate(route = "${NavRoutes.album.name}/${it.id}")
                        else navController.navigate(route = "${NavRoutes.onDeviceAlbum.name}/${it.id}")
                    },
                    onSearchClick = {
                        navController.navigate(NavRoutes.search.name)
                    },
                    onSettingsClick = {
                        navController.navigate(NavRoutes.settings.name)
                    }
                )

                13 -> HomePlaylists(
                    navController = navController,
                    onPlaylistClick = {
                        if (!it.isOnDevice)
                            navController.navigate(route = "${NavRoutes.localPlaylist.name}/${it.playlist.id}")
                        else
                            navController.navigate(route = "${NavRoutes.onDevicePlaylist.name}/${it.folder?.replace("/", "$")}")
                    },
                    onSearchClick = {
                        navController.navigate(NavRoutes.search.name)
                    },
                    onSettingsClick = {
                        navController.navigate(NavRoutes.settings.name)
                    }
                )

            }
        }
    }


    // Yammbo: Update dialogs disabled
    /*
    if(BuildConfig.BUILD_VARIANT == "full") {
        if (showNewversionDialog && checkUpdateState == CheckUpdateState.Enabled)
            CheckAvailableNewVersion(
                onDismiss = { showNewversionDialog = false },
                updateAvailable = {}
            )

        if (checkUpdateState == CheckUpdateState.Ask)
            ConfirmationDialog(
                text = stringResource(R.string.check_at_github_for_updates) + "\n\n" +
                        stringResource(R.string.when_an_update_is_available_you_will_be_asked_if_you_want_to_install_info) + "\n\n" +
                        stringResource(R.string.but_these_updates_would_not_go_through) + "\n\n" +
                        stringResource(R.string.you_can_still_turn_it_on_or_off_from_the_settings),
                confirmText = stringResource(R.string.enable),
                cancelText = stringResource(R.string.don_t_enable),
                cancelBackgroundPrimary = true,
                onCancel = { checkUpdateState = CheckUpdateState.Disabled },
                onDismiss = { checkUpdateState = CheckUpdateState.Disabled },
                onConfirm = { checkUpdateState = CheckUpdateState.Enabled },
            )
    }
    */

    // Exit app when user uses back
    val context = LocalContext.current
    var confirmCount by remember { mutableIntStateOf( 0 ) }
    val playerSheetState = LocalPlayerSheetState.current
    BackHandler(
        enabled = !playerSheetState.isExpanded
    ) {
        // Prevent this from being applied when user is not on HomeScreen
        if( NavRoutes.home.isNotHere( navController ) )  {
            if ( navController.currentBackStackEntry?.lifecycle?.currentState == Lifecycle.State.RESUMED )
                navController.popBackStack()

            return@BackHandler
        }

        if( confirmCount == 0 ) {
            SmartMessage(
                context.resources.getString(R.string.press_once_again_to_exit),
                context = context
            )
            confirmCount++

            // Reset confirmCount after 5s
            CoroutineScope( Dispatchers.Default ).launch {
                delay( 5000L )
                confirmCount = 0
            }
        } else {
            val activity = context as? Activity
            activity?.finishAffinity()
            // Close app with exit 0 notify that no problem occurred
            exitProcess( 0 )
        }
    }
}
