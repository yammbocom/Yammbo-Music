package it.fast4x.riplay.ui.screens.player.online

import androidx.annotation.OptIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import it.fast4x.riplay.enums.PlayerBackgroundColors
import it.fast4x.riplay.enums.PlayerControlsType
import it.fast4x.riplay.enums.PlayerPlayButtonType
import it.fast4x.riplay.ui.components.themed.PlaybackParamsDialog
import it.fast4x.riplay.ui.screens.player.online.components.controls.ControlsEssential
import it.fast4x.riplay.ui.screens.player.online.components.controls.ControlsModern
import it.fast4x.riplay.extensions.preferences.playbackDurationKey
import it.fast4x.riplay.extensions.preferences.playbackSpeedKey
import it.fast4x.riplay.extensions.preferences.playerBackgroundColorsKey
import it.fast4x.riplay.extensions.preferences.playerControlsTypeKey
import it.fast4x.riplay.extensions.preferences.playerPlayButtonTypeKey
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.LocalPlayerServiceBinder
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.isPodcast
import it.fast4x.riplay.utils.typography

@OptIn(UnstableApi::class)
@Composable
fun GetControls(
    position: Long,
    shouldBePlaying: Boolean,
    likedAt: Long?,
    mediaItem: MediaItem,
    onBlurScaleChange: (Float) -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onSeekTo: (Float) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onToggleRepeatMode: () -> Unit,
    onToggleShuffleMode: () -> Unit,
    playerState: PlayerConstants.PlayerState,
) {
    val playerControlsType by rememberPreference(
        playerControlsTypeKey,
        PlayerControlsType.Essential
    )
    val playerPlayButtonType by rememberPreference(
        playerPlayButtonTypeKey,
        PlayerPlayButtonType.Disabled
    )
    var isRotated by rememberSaveable { mutableStateOf(false) }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isRotated) 360F else 0f,
        animationSpec = tween(durationMillis = 200), label = ""
    )
    val playerBackgroundColors by rememberPreference(
        playerBackgroundColorsKey,
        PlayerBackgroundColors.CoverColorGradient
    )

    val isGradientBackgroundEnabled = playerBackgroundColors == PlayerBackgroundColors.ThemeColorGradient ||
            playerBackgroundColors == PlayerBackgroundColors.CoverColorGradient

    var playbackSpeed by rememberPreference(playbackSpeedKey, 1f)
//    var playbackDuration by rememberPreference(playbackDurationKey, 0f)
//    var setPlaybackDuration by remember { mutableStateOf(false) }

    var showSpeedPlayerDialog by rememberSaveable {
        mutableStateOf(false)
    }

    if (showSpeedPlayerDialog) {
        PlaybackParamsDialog(
            onDismiss = { showSpeedPlayerDialog = false },
            speedValue = { playbackSpeed = it },
            pitchValue = {},
            durationValue = {
//                playbackDuration = it
//                setPlaybackDuration = true
            },
            scaleValue = onBlurScaleChange
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
        modifier = Modifier
            .fillMaxWidth()
    ) {

        if (playerControlsType == PlayerControlsType.Essential)
            ControlsEssential(
                position = position,
                playbackSpeed = playbackSpeed,
                shouldBePlaying = shouldBePlaying,
                likedAt = likedAt,
                mediaItem = mediaItem,
                playerPlayButtonType = playerPlayButtonType,
                isGradientBackgroundEnabled = isGradientBackgroundEnabled,
                onShowSpeedPlayerDialog = { showSpeedPlayerDialog = true },
                onPlay = onPlay,
                onPause = onPause,
                onSeekTo = onSeekTo,
                onNext = onNext,
                onPrevious = onPrevious,
                onToggleShuffleMode = onToggleShuffleMode,
                playerState = playerState
            )

        if (playerControlsType == PlayerControlsType.Modern)
            ControlsModern(
                position = position,
                playbackSpeed = playbackSpeed,
                shouldBePlaying = shouldBePlaying,
                playerPlayButtonType = playerPlayButtonType,
                isGradientBackgroundEnabled = isGradientBackgroundEnabled,
                onShowSpeedPlayerDialog = { showSpeedPlayerDialog = true },
                onPlay = onPlay,
                onPause = onPause,
                onNext = onNext,
                onPrevious = onPrevious,
                playerState = playerState
            )
    }

        // Podcast-only quick controls: speed cycle + 15s back / 30s forward.
        // Only shown for podcast episodes so the normal music player is untouched.
        if (mediaItem.isPodcast) {
            PodcastControls(
                position = position,
                playbackSpeed = playbackSpeed,
                onSetSpeed = { playbackSpeed = it },
                onSeekTo = onSeekTo,
            )
        }
    }
}

@Composable
private fun PodcastControls(
    position: Long,
    playbackSpeed: Float,
    onSetSpeed: (Float) -> Unit,
    onSeekTo: (Float) -> Unit,
) {
    val binder = LocalPlayerServiceBinder.current
    val speeds = listOf(1f, 1.25f, 1.5f, 1.75f, 2f)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
    ) {
        // Rewind 15s (position is in seconds for online playback)
        PodcastPill(text = "-15s") {
            onSeekTo((position - 15).coerceAtLeast(0).toFloat())
        }
        // Playback speed cycle
        PodcastPill(
            text = playbackSpeed.toString().removeSuffix(".0") + "x",
            emphasized = true
        ) {
            val currentIdx = speeds.indexOfFirst { kotlin.math.abs(it - playbackSpeed) < 0.01f }
            val nextSpeed = if (currentIdx == -1) 1.5f else speeds[(currentIdx + 1) % speeds.size]
            onSetSpeed(nextSpeed)
        }
        // Forward 30s, clamped to the episode duration when known
        PodcastPill(text = "+30s") {
            val duration = binder?.onlinePlayerCurrentDuration ?: 0f
            val target = (position + 30).toFloat()
            onSeekTo(if (duration > 0f) target.coerceAtMost(duration) else target)
        }
    }
}

@Composable
private fun PodcastPill(
    text: String,
    emphasized: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(colorPalette().text.copy(alpha = if (emphasized) 0.16f else 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        BasicText(
            text = text,
            style = typography().xs.copy(
                color = colorPalette().text,
                fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium
            )
        )
    }
}