package it.fast4x.riplay.service

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener

/**
 * Sends http(s) opens straight to the network and everything else to the cache-backed source.
 *
 * The only http(s) uris that reach the local player are live radio streams. They must not go
 * through CacheDataSource: an Icecast stream has no length, so the cache would write it to disk
 * for as long as it plays, and HLS segments would churn the LRU cache for nothing.
 */
@UnstableApi
internal class NetworkOrCacheDataSource(
    private val cached: DataSource,
    private val network: DataSource,
) : DataSource {
    private var active: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        cached.addTransferListener(transferListener)
        network.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val target = if (dataSpec.uri.isNetwork) network else cached
        active = target
        return target.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        active?.read(buffer, offset, length) ?: C.RESULT_END_OF_INPUT

    override fun getUri(): Uri? = active?.uri

    override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

    override fun close() {
        val source = active
        active = null
        source?.close()
    }

    class Factory(
        private val cached: DataSource.Factory,
        private val network: DataSource.Factory,
    ) : DataSource.Factory {
        override fun createDataSource(): DataSource =
            NetworkOrCacheDataSource(cached.createDataSource(), network.createDataSource())
    }
}

internal val Uri.isNetwork: Boolean
    get() = scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)
