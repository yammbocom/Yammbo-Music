package it.fast4x.environment.utils

import it.fast4x.environment.Environment
import it.fast4x.environment.models.Context
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import java.io.IOException
import java.net.Proxy

private class NewPipeDownloaderImpl(proxy: Proxy?) : Downloader() {

    private val client = OkHttpClient.Builder()
        .proxy(proxy)
        .build()

    @Throws(IOException::class, ReCaptchaException::class)
    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val requestBuilder = okhttp3.Request.Builder()
            .method(httpMethod, dataToSend?.toRequestBody())
            .url(url)
            .addHeader("User-Agent", Context.USER_AGENT)

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.size > 1) {
                requestBuilder.removeHeader(headerName)
                headerValueList.forEach { headerValue ->
                    requestBuilder.addHeader(headerName, headerValue)
                }
            } else if (headerValueList.size == 1) {
                requestBuilder.header(headerName, headerValueList[0])
            }
        }

        val response = client.newCall(requestBuilder.build()).execute()

        if (response.code == 429) {
            response.close()

            throw ReCaptchaException("NewPipe in Environment reCaptcha Challenge requested", url)
        }

        val responseBodyToReturn = response.body?.string()

        val latestUrl = response.request.url.toString()
        return Response(response.code, response.message, response.headers.toMultimap(), responseBodyToReturn, latestUrl)
    }

}

object NewPipeUtils {

    init {
        NewPipe.init(NewPipeDownloaderImpl(Environment.proxy))
    }

    fun getSignatureTimestamp(videoId: String): Result<Int> = runCatching {
        YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId)
    }

    /**
     * The best audio-only file of a video that can be fetched with plain HTTP range requests.
     *
     * Measured on 2026-10-07: v0.25.2 returned NO audio streams at all for YouTube Music songs;
     * v0.26.5 (with the SABR workaround client) returned them for 10 of 10 songs. The URL
     * expires and YouTube sometimes answers one range with 403: calling this again gives a
     * fresh URL that resumes fine at the same byte.
     *
     * m4a first: it is the one the app can tag and every Android version plays.
     */
    fun bestAudioStream(videoId: String): Result<AudioStreamData> = runCatching {
        val info = StreamInfo.getInfo(ServiceList.YouTube, "https://www.youtube.com/watch?v=$videoId")
        val candidates = info.audioStreams.filter {
            it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                (it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL)
        }
        val best = candidates.filter { it.format == MediaFormat.M4A }.maxByOrNull { it.averageBitrate }
            ?: candidates.filter { it.format == MediaFormat.WEBMA_OPUS || it.format == MediaFormat.WEBMA }
                .maxByOrNull { it.averageBitrate }
            ?: error("No downloadable audio stream for $videoId (${info.audioStreams.size} streams)")
        AudioStreamData(
            url = best.content,
            contentLength = best.itagItem?.contentLength ?: -1L,
            mimeType = best.format?.mimeType ?: "audio/mp4",
            extension = best.format?.suffix ?: "m4a",
            itag = best.itag,
            bitrate = best.averageBitrate.toLong() * 1000L,
            durationSeconds = info.duration,
        )
    }

}

data class AudioStreamData(
    val url: String,
    /** Exact size in bytes as announced by YouTube; -1 when unknown. */
    val contentLength: Long,
    val mimeType: String,
    val extension: String,
    val itag: Int,
    val bitrate: Long,
    val durationSeconds: Long,
)
