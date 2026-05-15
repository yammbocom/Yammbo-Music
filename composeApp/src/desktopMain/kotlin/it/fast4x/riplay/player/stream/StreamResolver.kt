package it.fast4x.riplay.player.stream

import it.fast4x.environment.EnvironmentExt
import it.fast4x.environment.models.Context
import it.fast4x.environment.models.PlayerResponse
import it.fast4x.environment.utils.NewPipeUtils

private val iosClient = Context.Client(
    clientName = "IOS",
    clientVersion = "19.29.1",
    osName = "iPhone",
    osVersion = "17.5.1.21F90",
    deviceMake = "Apple",
    deviceModel = "iPhone16,2",
    userAgent = "com.google.ios.youtube/19.29.1 (iPhone16,2; U; CPU iOS 17_5_1 like Mac OS X;)",
    xClientName = 5,
)

private val tvEmbeddedClient = Context.Client(
    clientName = "TVHTML5_SIMPLY_EMBEDDED_PLAYER",
    clientVersion = "2.0",
    userAgent = "Mozilla/5.0 (PlayStation; PlayStation 4/12.00) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.4 Safari/605.1.15",
    useSignatureTimestamp = true,
    isEmbedded = true,
    xClientName = 85,
)

suspend fun resolveStreamUrl(videoId: String): String? {
    if (videoId.isEmpty()) return null
    val signatureTimestamp = NewPipeUtils.getSignatureTimestamp(videoId).getOrNull()
    val clients = listOf(
        "IOS" to iosClient,
        "TV_EMBEDDED" to tvEmbeddedClient,
        "WEB_REMIX" to Context.DefaultWeb.client,
    )
    for ((name, client) in clients) {
        val response = EnvironmentExt.simpleMetadataPlayer(
            videoId = videoId,
            playlistId = null,
            client = client,
            signatureTimestamp = signatureTimestamp,
        ).getOrNull()
        if (response == null) {
            println("StreamResolver[$name]: request failed for $videoId")
        } else {
            val url = selectPlayableUrl(response)
            if (url != null) {
                println("StreamResolver[$name]: $videoId resolved")
                return url
            }
            println("StreamResolver[$name]: no raw url for $videoId; status=${response.playabilityStatus?.status}")
        }
    }
    return null
}

private fun selectPlayableUrl(response: PlayerResponse): String? {
    val format = response.streamingData?.autoMaxQualityFormat ?: return null
    return format.url
}
