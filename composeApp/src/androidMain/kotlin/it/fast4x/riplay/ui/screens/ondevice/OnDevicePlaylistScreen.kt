package it.fast4x.riplay.ui.screens.ondevice

import com.yambo.music.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavController
import com.github.doyaaaaaken.kotlincsv.client.KotlinCsvExperimental
import it.fast4x.riplay.extensions.preferences.disableScrollingTextKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.components.PageContainer
import it.fast4x.riplay.ui.components.ScreenContainer

@OptIn(KotlinCsvExperimental::class)
@ExperimentalMaterialApi
@ExperimentalTextApi
@ExperimentalFoundationApi
@ExperimentalAnimationApi
@ExperimentalComposeUiApi
@UnstableApi
@Composable
fun OnDevicePlaylistScreen(
    navController: NavController,
    folder: String,
    miniPlayer: @Composable () -> Unit = {},
) {
    PageContainer(
        navController = navController,
        miniPlayer = miniPlayer,
        noirTopBar = true,
        topBarTitle = stringResource(R.string.on_device),
    ) {
        OnDevicePlaylist(
            navController = navController,
            folder = folder
        )
    }

}
