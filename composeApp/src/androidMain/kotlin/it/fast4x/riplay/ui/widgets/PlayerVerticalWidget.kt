package it.fast4x.riplay.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.Text
import it.fast4x.riplay.MainActivity
import com.yambo.music.R
import it.fast4x.riplay.service.PlayerService
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.playNext
import it.fast4x.riplay.utils.playPrevious
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber

@UnstableApi
class PlayerVerticalWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PlayerVerticalWidget()
}

@UnstableApi
class PlayerVerticalWidget: GlanceAppWidget() {
    companion object {
        val songTitleKey = stringPreferencesKey("songTitleKey")
        val songArtistKey = stringPreferencesKey("songArtistKey")
        val isPlayingKey = booleanPreferencesKey("isPlayingKey")
        var widgetBitmap: Bitmap? = createBitmap(1, 1)
        var widgetBinder: PlayerService.Binder? = null
    }

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        provideContent {
            GlanceTheme {
                val preferences = currentState<Preferences>()
                Column(
                    modifier = GlanceModifier.fillMaxWidth()
                        .background(GlanceTheme.colors.widgetBackground)
                        .padding(4.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(text = preferences[songTitleKey] ?: "", modifier = GlanceModifier)
                    Text(text = preferences[songArtistKey] ?: "", modifier = GlanceModifier)
                    //Text(text = "isPlaying: ${preferences[isPlayingKey]}", modifier = GlanceModifier)

                    Row(
                        modifier = GlanceModifier.fillMaxWidth()
                            .background(GlanceTheme.colors.widgetBackground)
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        Image(
                            provider = ImageProvider(R.drawable.play_skip_back),
                            contentDescription = "back",
                            modifier = GlanceModifier
                                .clickable {
                                    widgetBinder?.player?.playPrevious()
                                }
                        )

                        Image(
                            provider = ImageProvider(
                                if (preferences[isPlayingKey] == true) {
                                    R.drawable.pause
                                } else {
                                    R.drawable.play
                                }
                            ),
                            contentDescription = "play/pause",
                            modifier = GlanceModifier.padding(horizontal = 20.dp)
                                .clickable {
                                    widgetBinder?.let { widgetBinder ->
                                        if (preferences[isPlayingKey] == true) {
                                            widgetBinder.player.pause()
                                            widgetBinder.onlinePlayer?.pause()
                                        } else {
                                            if (widgetBinder.currentMediaItemAsSong?.isLocal == true)
                                                widgetBinder.player.play()
                                            else
                                                widgetBinder.onlinePlayer?.play()
                                        }
                                    }
                                }
                        )

                        Image(
                            provider = ImageProvider(R.drawable.play_skip_forward),
                            contentDescription = "next",
                            modifier = GlanceModifier
                                .clickable {
                                    widgetBinder?.player?.playNext()
                                }
                        )

                    }


                    // Real cover when loaded; Yammbo launcher icon instead of the 1x1
                    // placeholder bitmap (was rendered as a blank square).
                    val cover = widgetBitmap?.takeIf { it.width > 1 }
                    Image(
                        provider = if (cover != null) ImageProvider(cover)
                            else ImageProvider(R.mipmap.ic_launcher),
                        contentDescription = "cover",
                        modifier = GlanceModifier.padding(horizontal = 5.dp)
                            .clickable (
                                onClick = actionStartActivity<MainActivity>()
                            )

                    )


                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    suspend fun updateInfo(
        context: Context,
        isPlaying: Boolean,
        songTitle: String,
        songArtist: String,
        bitmap: Bitmap?,
        binder: PlayerService.Binder
    ) {

        // Every placed instance (the old code only refreshed the first one),
        // and write the state BEFORE update() so the render sees fresh data.
        // Title/artist arrive as plain strings: this lambda runs on the
        // DataStore IO thread, where the player must not be touched.
        val glanceIds =
            GlanceAppWidgetManager(context).getGlanceIds(PlayerVerticalWidget::class.java)
        if (glanceIds.isEmpty()) return

        if (bitmap != null) widgetBitmap = bitmap
        widgetBinder = binder

        glanceIds.forEach { glanceId ->
            updateAppWidgetState(
                context,
                PreferencesGlanceStateDefinition,
                glanceId
            ) { preferences ->
                preferences.toMutablePreferences().apply {
                    this[songTitleKey] = songTitle
                    this[songArtistKey] = songArtist
                    this[isPlayingKey] = isPlaying
                }
            }
            PlayerVerticalWidget().update(context, glanceId)
        }
    }

}
