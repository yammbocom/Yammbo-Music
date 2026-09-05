package it.fast4x.riplay.ui.widgets

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.SizeMode
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.common.util.UnstableApi
import com.yambo.music.R
import it.fast4x.riplay.MainActivity
import it.fast4x.riplay.service.PlayerService
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.playNext
import it.fast4x.riplay.utils.playPrevious

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

    // Re-compose per exact size so the layout scales when the user resizes
    // the widget instead of keeping the default-cell proportions.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {

        provideContent {
            val preferences = currentState<Preferences>()
            val widgetSize = LocalSize.current
            // Cover scales with the widget, leaving room for texts+controls.
            val coverSize = minOf(
                widgetSize.width - 32.dp,
                (widgetSize.height - 110.dp).coerceAtLeast(80.dp)
            ).coerceIn(80.dp, 220.dp)
            val isPlaying = preferences[isPlayingKey] == true
            val title = preferences[songTitleKey]
                ?.takeIf { it.isNotBlank() && it != "null" } ?: "Yammbo Music"
            val artist = preferences[songArtistKey]
                ?.takeIf { it.isNotBlank() && it != "null" } ?: ""
            // Real cover when loaded; Yammbo launcher icon instead of the
            // 1x1 placeholder bitmap.
            val cover = widgetBitmap?.takeIf { it.width > 1 }

            Column(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(YammboWidgetPalette.background)
                    .cornerRadius(20.dp)
                    .padding(12.dp)
                    .clickable(onClick = actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(coverSize)
                        .cornerRadius(14.dp)
                        .background(YammboWidgetPalette.coverPlaceholder),
                    contentAlignment = Alignment.Center
                ) {
                    if (cover != null)
                        Image(
                            provider = ImageProvider(cover),
                            contentDescription = "cover",
                            contentScale = ContentScale.Crop,
                            modifier = GlanceModifier.fillMaxSize()
                        )
                    else
                        // Visible placeholder: the launcher icon is black and
                        // disappeared against the black card.
                        Image(
                            provider = ImageProvider(R.drawable.musical_notes),
                            contentDescription = "cover",
                            colorFilter = ColorFilter.tint(ColorProvider(YammboWidgetPalette.text)),
                            modifier = GlanceModifier.size(40.dp)
                        )
                }

                Spacer(modifier = GlanceModifier.height(8.dp))

                Text(
                    text = title,
                    style = TextStyle(
                        color = ColorProvider(YammboWidgetPalette.text),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.fillMaxWidth()
                )
                if (artist.isNotEmpty())
                    Text(
                        text = artist,
                        style = TextStyle(
                            color = ColorProvider(YammboWidgetPalette.textSecondary),
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        ),
                        maxLines = 1,
                        modifier = GlanceModifier.fillMaxWidth()
                    )

                Spacer(modifier = GlanceModifier.height(10.dp))

                WidgetPlaybackControls(
                    isPlaying = isPlaying,
                    onPrevious = { widgetBinder?.player?.playPrevious() },
                    onPlayPause = {
                        widgetBinder?.let { binder ->
                            if (isPlaying) {
                                binder.player.pause()
                                binder.onlinePlayerPause()
                            } else {
                                if (binder.currentMediaItemAsSong?.isLocal == true)
                                    binder.player.play()
                                else
                                    binder.onlinePlayerPlay()
                            }
                        }
                    },
                    onNext = { widgetBinder?.player?.playNext() }
                )
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
