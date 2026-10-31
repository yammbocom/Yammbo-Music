package it.fast4x.riplay.commonutils

import coil3.Uri
import coil3.toUri
import kotlin.concurrent.Volatile

const val PINNED_PREFIX = "pinned:"
const val MODIFIED_PREFIX = "modified:"
const val MONTHLY_PREFIX = "monthly:"
const val PIPED_PREFIX = "piped:"
const val EXPLICIT_PREFIX = "e:"
const val LOCAL_KEY_PREFIX = "local:"
const val YTP_PREFIX = "account:"

const val YT_PLAYLIST_SHARE_BASEURL = "https://www.youtube.com/playlist?list="
const val YTM_PLAYLIST_SHARE_BASEURL = "https://music.youtube.com/playlist?list="
const val YT_VIDEOORSONG_SHARE_BASEURL = "https://www.youtube.com/watch?v="
const val YTM_VIDEOORSONG_SHARE_BASEURL = "https://music.youtube.com/watch?v="
const val YT_ARTIST_SHARE_BASEURL = "https://www.youtube.com/channel/"
const val YTM_ARTIST_SHARE_BASEURL = "https://music.youtube.com/channel/"
const val YT_ALBUM_SHARE_BASEURL = "https://www.youtube.com/browse/"
const val YTM_ALBUM_SHARE_BASEURL = "https://music.youtube.com/browse/"

const val YAMBO_TRACK_SHARE_BASEURL = "https://music.yammbo.com/track/"
const val YAMBO_ARTIST_SHARE_BASEURL = "https://music.yammbo.com/artist/"
const val YAMBO_ALBUM_SHARE_BASEURL = "https://music.yammbo.com/album/"
const val YAMBO_PLAYLIST_SHARE_BASEURL = "https://music.yammbo.com/playlist/"

fun slugify(text: String): String {
    return cleanPrefix(text).lowercase()
        .replace(Regex("[^a-z0-9\\s-]"), "")
        .trim()
        .replace(Regex("\\s+"), "-")
        .replace(Regex("-+"), "-")
}


/**
 * Assumption: all prefixes end with ":" and have at least 1 (other) character.
 * Removes a "prefix of prefixes" including multiple times the same prefix (at different locations).
 */
fun cleanPrefix(text: String): String {
    val splitText = text.split(":")
    var i = 0
    while (i < splitText.size-1) {
        if ("${splitText[i]}:" !in listOf(PINNED_PREFIX, MODIFIED_PREFIX, MONTHLY_PREFIX, PIPED_PREFIX,
                EXPLICIT_PREFIX, LOCAL_KEY_PREFIX, YTP_PREFIX)) {
            break
        }
        i++
    }
    if(i >= splitText.size) return ""
    return splitText.subList(i, splitText.size).joinToString(":")
}

fun String.removePrefix(): String {
    return cleanPrefix(this)
}

fun cleanString(text: String): String {
    var cleanText = text.replace("/", "", true)
    cleanText = cleanText.replace("#", "", true)
    cleanText = cleanText.replace("?", "", true)
    cleanText = cleanText.replace(":", "", true)
    cleanText = cleanText.replace(";", "", true)
    cleanText = cleanText.replace("'", "", true)
    cleanText = cleanText.replace("\"", "", true)
    cleanText = cleanText.replace("!", "", true)
    cleanText = cleanText.replace("(", "", true)
    cleanText = cleanText.replace(")", "", true)
    return cleanText
}

/**
 * Size the player asks the CDN for.
 *
 * The full-screen cover, the blurred backdrop behind it and the palette the
 * background gradient is sampled from are all the same image, so they have to
 * agree: a mismatch turns one download into two or three. Measured on YouTube's
 * CDN, one cover is ~208KB at 1200px against ~156KB here, and the player is not
 * shown at a size where the difference is visible.
 */
const val PLAYER_COVER_PX = 800

/**
 * Process-wide knobs for the CDN image size. Set from MainApplication:
 * [densityFactor] = displayDensity / 3 (clamped), because the px values passed to
 * thumbnail() were tuned for ~3x screens; [dataSaver] is true only while the
 * "Ahorro de datos" preference is on AND the connection is metered.
 */
object ThumbnailSizing {
    @Volatile var densityFactor: Float = 1f
    @Volatile var dataSaver: Boolean = false

    private val buckets = intArrayOf(96, 160, 240, 320, 480, 640, 800, 1200)

    /** Scales [px] for this screen / mode and snaps UP to a shared bucket so the cached URL is reused. */
    fun effective(px: Int): Int {
        val scaled = px * densityFactor * (if (dataSaver) 0.75f else 1f)
        return buckets.firstOrNull { it >= scaled } ?: buckets.last()
    }
}

private val googleCdnSized = Regex("(https://(?:lh3|yt3)\\.googleusercontent\\.com/[^=]+)=[^/]*")
private val ytimgVideoThumb = Regex("^(https://i\\.ytimg\\.com/vi/[^/?#]+/)[A-Za-z0-9_]+\\.jpg(?:[?#].*)?$")

/** Size-adapted (density / data saver aware, bucketed) thumbnail URL. */
fun String?.thumbnail(size: Int): String? {
    val s = this ?: return null
    return s.thumbnailExact(ThumbnailSizing.effective(size))
}

/** Thumbnail URL at exactly [size] px (no density / data saver scaling). */
fun String?.thumbnailExact(size: Int): String? {
    val s = this ?: return null
    // Any size/crop suffix the CDN already carries (=s576, =w544-h544-l90-rj,
    // =w544-c-h544-k-c0x00ffffff-no-l90-rj...) is replaced: a second "=..." answers 400.
    googleCdnSized.matchEntire(s)?.let { match ->
        return "${match.groupValues[1]}=w$size-h$size-p-l90-rj"
    }
    // Video thumbnails: pick the variant by size. mqdefault (320x180) and hq720 are
    // 16:9 without letterbox; hqdefault/sddefault are 4:3 with black bars that show up
    // in square crops, so they are never produced here.
    ytimgVideoThumb.matchEntire(s)?.let { match ->
        return match.groupValues[1] + (if (size <= 320) "mqdefault.jpg" else "hq720.jpg")
    }
    return when {
        s.startsWith("https://lh3.googleusercontent.com") ||
        s.startsWith("https://yt3.googleusercontent.com") ->
            "$s=w$size-h$size-p-l90-rj"
        s.startsWith("https://i.ytimg.com") &&
            !s.contains("/podcasts_artwork/") &&
            !s.contains("/pl_c/") &&
            !s.contains("/vi/") ->
            "$s=w$size-h$size"
        s.startsWith("https://yt3.ggpht.com") && s.endsWith("-rj") ->
            "$s-w$size-h$size-s$size"
        s.startsWith("https://yt3.ggpht.com") ->
            "${s.substringBeforeLast("-rj")}-rj-w$size-h$size-s$size"
        else -> s
    }
}
fun String?.thumbnail(): String? {
    return this
}

fun Uri?.thumbnail(size: Int): Uri? {
    return toString().thumbnail(size)?.toUri()
}

fun durationToMillis(duration: String): Long {
    val parts = duration.split(":")
    if (parts.size == 3){
        val hours = parts[0].toLong()
        val minutes = parts[1].toLong()
        val seconds = parts[2].toLong()
        return hours * 3600000 + minutes * 60000 + seconds * 1000
    } else {
        val minutes = parts[0].toLong()
        val seconds = parts[1].toLong()
        return minutes * 60000 + seconds * 1000
    }
}

fun durationTextToMillis(duration: String): Long {
    return try {
        durationToMillis(duration)
    } catch (e: Exception) {
        0L
    }
}