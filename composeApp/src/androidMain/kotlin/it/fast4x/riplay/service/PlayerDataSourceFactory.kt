package it.fast4x.riplay.service

import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.yambo.music.BuildConfig
import it.fast4x.environment.Environment
import it.fast4x.riplay.data.Database
import it.fast4x.riplay.utils.LOCAL_KEY_PREFIX
import it.fast4x.riplay.utils.asSong
import it.fast4x.riplay.utils.isAtLeastAndroid10
import it.fast4x.riplay.utils.isLocal
import it.fast4x.riplay.utils.isLocalUri
import it.fast4x.riplay.utils.isRadio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import timber.log.Timber
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
internal fun PlayerService.createLocalDataSourceFactory(): DataSource.Factory {
    val upstream = NetworkOrCacheDataSource.Factory(
        cached = createLocalCacheDataSource(),
        network = createRadioStreamDataSourceFactory(),
    )
    return ResolvingDataSource.Factory(upstream) { dataSpec ->

        Timber.d("createLocalDataSourceFactory dataSpec: uri ${dataSpec.uri} isLocalUri ${dataSpec.isLocalUri} isLocal: ${dataSpec.isLocal}")

        // Get current song from player, is same as current dataSpec
        val mediaItem = runBlocking {
            withContext(Dispatchers.Main) {
                player.currentMediaItem
            }
        }

        if (dataSpec.uri.isNetwork) {
            // Live radio. The stream is the only http(s) uri this player ever sees (online songs
            // are YouTube ids without a scheme and are refused below, as always). Register the
            // listen once, on the top-level request: HLS opens one request per segment.
            if (mediaItem != null && mediaItem.isRadio && mediaItem.localConfiguration?.uri == dataSpec.uri) {
                Database.asyncTransaction { insert(mediaItem.asSong) }
            }
            return@Factory dataSpec
        }

        // Ensure that the song is in database
        Database.asyncTransaction {
            if (mediaItem != null) {
                insert(mediaItem.asSong)
            }
        }

        when {
            dataSpec.isLocal && dataSpec.isLocalUri -> {
                Timber.d("createLocalDataSourceFactory dataSpec.isLocalUri: YES")
                return@Factory dataSpec
            }
            dataSpec.isLocal && !dataSpec.isLocalUri-> {
                val contentUriBase =
                    if (isAtLeastAndroid10) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
                    else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                val id = dataSpec.key?.removePrefix(LOCAL_KEY_PREFIX)
                val contentUri = contentUriBase.buildUpon().appendPath(id).build()
                Timber.d("createLocalDataSourceFactory dataSpec.isLocal: yes contentUri: $contentUri")
                return@Factory dataSpec.withUri(contentUri)
            }
            else -> {
                throw PlaybackException(
                    "File not exists or not on device",
                    Throwable(),
                    PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
                )
            }
        }

    }
}

/**
 * Plain network source for live radio: no cache in front of it, generous read timeout because a
 * quiet station legitimately sends nothing for a while, and a real User-Agent because some
 * Icecast hosts refuse the default one.
 */
@OptIn(UnstableApi::class)
internal fun PlayerService.createRadioStreamDataSourceFactory(): DataSource.Factory =
    DefaultDataSource.Factory(
        this,
        OkHttpDataSource.Factory(
            OkHttpClient.Builder()
                .proxy(Environment.proxy)
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build(),
        ).setUserAgent("YammboMusic/${BuildConfig.VERSION_NAME} (Android)"),
    )
