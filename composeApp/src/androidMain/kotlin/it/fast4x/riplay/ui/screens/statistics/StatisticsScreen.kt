package it.fast4x.riplay.ui.screens.statistics

import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import it.fast4x.riplay.extensions.persist.PersistMapCleanup
import com.yambo.music.R
import it.fast4x.riplay.enums.StatisticsType
import it.fast4x.riplay.ui.components.ScreenContainer
import it.fast4x.riplay.ui.screens.search.openHomeTab

@ExperimentalMaterialApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun StatisticsScreen(
    navController: NavController,
    statisticsType: StatisticsType,
    miniPlayer: @Composable () -> Unit = {},
) {
    val context = LocalContext.current

    val (tabIndex, onTabIndexChanged) = rememberSaveable {
        mutableStateOf(when (statisticsType) {
            StatisticsType.Today -> 0
            StatisticsType.OneWeek -> 1
            StatisticsType.OneMonth -> 2
            StatisticsType.ThreeMonths -> 3
            StatisticsType.SixMonths -> 4
            StatisticsType.OneYear -> 5
            StatisticsType.All -> 6
        })
    }

    PersistMapCleanup(tagPrefix = "${statisticsType.name}/")

    // Yammbo Noir: the bar is the app's own pill; the periods are chips in the page, so the
    // container's tab stays fixed and changing the period only swaps the lists below the chips.
    ScreenContainer(
        navController = navController,
        tabIndex = 0,
        miniPlayer = miniPlayer,
        navBarTabIndex = 2,
        onNavBarTabChanged = { index -> openHomeTab(navController, context, index) },
        sharedChrome = true,
        noirTopBar = true,
        navBarContent = { item ->
            item(0, stringResource(R.string.home), R.drawable.home)
            item(1, stringResource(R.string.live_radio_tab), R.drawable.radio)
            item(5, stringResource(R.string.podcasts_tab_podcasts), R.drawable.podcasts_tab)
            item(2, stringResource(R.string.my_music), R.drawable.musical_notes)
            item(3, stringResource(R.string.search), R.drawable.search)
        }
    ) { _ ->
        val type: StatisticsType =
            when (tabIndex) {
                0 -> StatisticsType.Today
                1 -> StatisticsType.OneWeek
                2 -> StatisticsType.OneMonth
                3 -> StatisticsType.ThreeMonths
                4 -> StatisticsType.SixMonths
                5 -> StatisticsType.OneYear
                else -> StatisticsType.All
            }

        StatisticsPage(
            navController = navController,
            statisticsType = type,
            onStatisticsTypeChange = { newType -> onTabIndexChanged(newType.ordinal) }
        )
    }
}
