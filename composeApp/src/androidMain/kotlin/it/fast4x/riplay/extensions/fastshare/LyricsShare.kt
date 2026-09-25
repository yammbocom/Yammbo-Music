package it.fast4x.riplay.extensions.fastshare

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import android.graphics.LinearGradient
import android.graphics.Shader
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Most lines a lyrics card takes: more than this stops reading as a quote. */
private const val MAX_SHARED_LINES = 5

private val lrcTimestamp = Regex("""^(\[\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?])+""")
private val lrcTimeParts = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

/** A lyric line, with its start time when the lyrics are synchronised. */
data class ShareableLyricLine(val startMs: Long?, val text: String)

/**
 * Splits raw lyrics (LRC or plain) into shareable lines, dropping empty lines and LRC
 * header tags such as [ar:...].
 */
fun parseShareableLyrics(raw: String?): List<ShareableLyricLine> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.lines().mapNotNull { line ->
        val trimmed = line.trim()
        val stamp = lrcTimestamp.find(trimmed)
        if (stamp != null) {
            val text = trimmed.substring(stamp.value.length).trim()
            val start = lrcTimeParts.find(stamp.value)?.let { m ->
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = if (frac.isEmpty()) 0L else frac.padEnd(3, '0').take(3).toLong()
                (min * 60 + sec) * 1000 + ms
            }
            text.takeIf { it.isNotEmpty() }?.let { ShareableLyricLine(start, it) }
        } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
            null // [ar:...], [ti:...] and friends
        } else {
            trimmed.takeIf { it.isNotEmpty() }?.let { ShareableLyricLine(null, it) }
        }
    }
}

/** Index of the line being sung at [positionMs], or 0 for plain lyrics. */
fun currentLyricIndex(lines: List<ShareableLyricLine>, positionMs: Long): Int {
    if (lines.none { it.startMs != null }) return 0
    return lines.indexOfLast { (it.startMs ?: Long.MAX_VALUE) <= positionMs }.coerceAtLeast(0)
}

/** Background treatments offered in the preview. Cover is the default, like Spotify's cards. */
enum class LyricsCardStyle { Cover, Gradient, Dark, Light }

/**
 * Sheet body for sharing lyrics, hosted by the global menu sheet. Step one picks the lines
 * (the one being sung is picked already); step two previews the image with a choice of
 * background before anything leaves the app.
 */
@Composable
fun ShareLyricsPicker(
    lines: List<ShareableLyricLine>,
    initialIndex: Int,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val selected = remember {
        mutableStateListOf<Int>().apply { if (lines.isNotEmpty()) add(initialIndex.coerceIn(0, lines.lastIndex)) }
    }
    var previewing by remember { mutableStateOf(false) }
    var style by remember { mutableStateOf(LyricsCardStyle.Cover) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(previewing, style) {
        if (!previewing) return@LaunchedEffect
        isGenerating = true
        previewUri = LyricsCardGenerator.generate(
            context, selected.sorted().map { lines[it].text }, title, artist, thumbnailUrl, style
        )
        isGenerating = false
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colorPalette().background1)
            .navigationBarsPadding()
            .padding(bottom = 12.dp)
    ) {
        BasicText(
            text = stringResource(if (previewing) R.string.share_preview_title else R.string.share_lyrics_title),
            style = typography().m.semiBold.copy(color = colorPalette().text),
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        if (!previewing) {
            BasicText(
                text = stringResource(R.string.share_lyrics_hint, MAX_SHARED_LINES),
                style = typography().xs.copy(color = colorPalette().textSecondary),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(scrollState)
                    .padding(vertical = 8.dp)
            ) {
                lines.forEachIndexed { index, line ->
                    val isSelected = index in selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (isSelected) selected.remove(index)
                                else if (selected.size < MAX_SHARED_LINES) selected.add(index)
                            }
                            .heightIn(min = 44.dp)
                            .padding(horizontal = 24.dp, vertical = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) colorPalette().text else colorPalette().background3)
                        )
                        BasicText(
                            text = line.text,
                            style = (if (isSelected) typography().s.semiBold else typography().s)
                                .copy(color = if (isSelected) colorPalette().text else colorPalette().textSecondary),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            PrimaryPill(
                text = stringResource(R.string.share_preview_action),
                enabled = selected.isNotEmpty(),
                onClick = { previewUri = null; previewing = true }
            )
        } else {
            // The image as it will be posted, 9:16 like a story.
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .height(340.dp)
            ) {
                val frame = Modifier
                    .fillMaxHeight()
                    .aspectRatio(9f / 16f, matchHeightConstraintsFirst = true)
                    .clip(RoundedCornerShape(14.dp))
                if (previewUri != null)
                    AsyncImage(model = previewUri, contentDescription = null, modifier = frame)
                else
                    Box(modifier = frame.background(colorPalette().background2))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                LyricsCardStyle.entries.forEach { option ->
                    val isOn = option == style
                    BasicText(
                        text = stringResource(
                            when (option) {
                                LyricsCardStyle.Cover -> R.string.lyrics_style_cover
                                LyricsCardStyle.Gradient -> R.string.lyrics_style_gradient
                                LyricsCardStyle.Dark -> R.string.lyrics_style_dark
                                LyricsCardStyle.Light -> R.string.lyrics_style_light
                            }
                        ),
                        style = typography().xs.semiBold.copy(
                            color = if (isOn) colorPalette().background0 else colorPalette().text
                        ),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (isOn) colorPalette().text else colorPalette().background2)
                            .clickable(enabled = !isGenerating) { style = option }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            PrimaryPill(
                text = stringResource(if (isGenerating) R.string.share_lyrics_working else R.string.share_sheet_title),
                enabled = previewUri != null && !isGenerating,
                onClick = {
                    previewUri?.let { shareLyricsImage(context, it, title, artist) }
                    onDismiss()
                }
            )
            BasicText(
                text = stringResource(R.string.share_change_lines),
                style = typography().xs.semiBold.copy(
                    color = colorPalette().textSecondary,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { previewing = false }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

@Composable
private fun PrimaryPill(text: String, enabled: Boolean, onClick: () -> Unit) {
    BasicText(
        text = text,
        style = typography().s.semiBold.copy(
            color = colorPalette().background0,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        ),
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(colorPalette().text)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = 14.dp)
    )
}

private fun shareLyricsImage(context: Context, uri: Uri, title: String, artist: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, if (artist.isBlank()) title else "$title - $artist")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    try {
        context.startActivity(
            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    } catch (_: ActivityNotFoundException) {
    }
}

/**
 * Story-sized lyrics card in the style people know from Spotify: the frame takes the cover's
 * own colour, a slightly deeper card carries the cover, the track and the quoted lines, and
 * the Yammbo Music mark sits at the bottom.
 */
object LyricsCardGenerator {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val CARD_LEFT = 90f
    private const val CARD_WIDTH = 900f
    private const val PAD = 64f
    private const val COVER = 132
    private const val RADIUS = 48f

    suspend fun generate(
        context: Context,
        lines: List<String>,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        style: LyricsCardStyle = LyricsCardStyle.Cover,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val cover = ShareImageGenerator.loadCoverArt(thumbnailUrl)
            val (base, second) = cover?.let { pickColors(it) } ?: (Color.rgb(40, 40, 40) to Color.rgb(20, 20, 20))
            val cardColor = when (style) {
                LyricsCardStyle.Cover -> ColorUtils.blendARGB(base, Color.BLACK, 0.18f)
                // Frosted dark card over the gradient, so both colours show through.
                LyricsCardStyle.Gradient -> Color.argb(70, 0, 0, 0)
                LyricsCardStyle.Dark -> Color.rgb(28, 28, 28)
                LyricsCardStyle.Light -> Color.WHITE
            }
            val ink = when (style) {
                LyricsCardStyle.Cover -> if (ColorUtils.calculateLuminance(cardColor) > 0.5) Color.BLACK else Color.WHITE
                LyricsCardStyle.Gradient, LyricsCardStyle.Dark -> Color.WHITE
                LyricsCardStyle.Light -> Color.BLACK
            }
            val inkSoft = ColorUtils.setAlphaComponent(ink, 190)

            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            when (style) {
                LyricsCardStyle.Cover -> canvas.drawColor(base)
                LyricsCardStyle.Gradient -> canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(),
                        base, second, Shader.TileMode.CLAMP
                    )
                })
                LyricsCardStyle.Dark -> canvas.drawColor(Color.rgb(12, 12, 12))
                LyricsCardStyle.Light -> canvas.drawColor(Color.rgb(238, 238, 238))
            }

            val innerWidth = (CARD_WIDTH - PAD * 2).toInt()

            val lyricPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                textSize = if (lines.sumOf { it.length } > 140) 58f else 68f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val lyricLayout = StaticLayout.Builder
                .obtain(lines.joinToString("\n"), 0, lines.joinToString("\n").length, lyricPaint, innerWidth)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(8f, 1f)
                .setMaxLines(14)
                .setEllipsize(android.text.TextUtils.TruncateAt.END)
                .build()

            val headerHeight = COVER.toFloat()
            val brandHeight = 56f
            val cardHeight = PAD + headerHeight + 64f + lyricLayout.height + 72f + brandHeight + PAD
            val cardTop = ((HEIGHT - cardHeight) / 2f).coerceAtLeast(260f)
            val card = RectF(CARD_LEFT, cardTop, CARD_LEFT + CARD_WIDTH, cardTop + cardHeight)

            canvas.drawRoundRect(card, RADIUS, RADIUS, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = cardColor
                setShadowLayer(50f, 0f, 18f, Color.argb(90, 0, 0, 0))
            })

            // Header: cover, title, artist
            val left = CARD_LEFT + PAD
            var y = cardTop + PAD
            if (cover != null) {
                val scaled = ShareImageGenerator.centerCropBitmap(cover, COVER)
                canvas.save()
                canvas.clipPath(android.graphics.Path().apply {
                    addRoundRect(RectF(left, y, left + COVER, y + COVER), 16f, 16f, android.graphics.Path.Direction.CW)
                })
                canvas.drawBitmap(scaled, left, y, null)
                canvas.restore()
                scaled.recycle()
            }
            val textLeft = left + (if (cover != null) COVER + 32f else 0f)
            val textWidth = CARD_LEFT + CARD_WIDTH - PAD - textLeft
            val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                textSize = 42f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSoft
                textSize = 36f
            }
            canvas.drawText(ellipsize(title, titlePaint, textWidth), textLeft, y + COVER / 2f - 8f, titlePaint)
            if (artist.isNotBlank())
                canvas.drawText(ellipsize(artist, artistPaint, textWidth), textLeft, y + COVER / 2f + 44f, artistPaint)

            // Quoted lines
            y += headerHeight + 64f
            canvas.save()
            canvas.translate(left, y)
            lyricLayout.draw(canvas)
            canvas.restore()

            // Brand
            y += lyricLayout.height + 72f
            drawBrand(context, canvas, left, y + brandHeight, ink)

            cover?.recycle()
            ShareImageGenerator.saveBitmapAndGetUri(context, bitmap)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Two colours from the cover: a rich, not-too-bright one for the frame (like a Spotify
     * lyrics card) and a deeper one for the far end of the gradient.
     */
    private fun pickColors(cover: Bitmap): Pair<Int, Int> {
        val palette = Palette.from(cover).maximumColorCount(16).generate()
        val main = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
        val deep = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
        return tame(main?.rgb ?: Color.DKGRAY, 0.32f, 0.55f) to tame(deep?.rgb ?: Color.BLACK, 0.12f, 0.28f)
    }

    /** Keeps the hue, clamps saturation and lightness so white or black text always reads. */
    private fun tame(color: Int, minLight: Float, maxLight: Float): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = hsl[1].coerceIn(0.25f, 0.65f)
        hsl[2] = hsl[2].coerceIn(minLight, maxLight)
        return ColorUtils.HSLToColor(hsl)
    }

    private fun drawBrand(context: Context, canvas: Canvas, left: Float, baseline: Float, ink: Int) {
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 36f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        var x = left
        runCatching {
            val icon = BitmapFactory.decodeResource(context.resources, R.drawable.yambo_icon)
            if (icon != null) {
                val size = 56
                val scaled = Bitmap.createScaledBitmap(icon, size, size, true)
                canvas.drawBitmap(scaled, x, baseline - size + 10f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    colorFilter = android.graphics.PorterDuffColorFilter(ink, android.graphics.PorterDuff.Mode.SRC_IN)
                })
                scaled.recycle()
                icon.recycle()
                x += size + 16f
            }
        }
        canvas.drawText("Yammbo Music", x, baseline, textPaint)
    }

    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }
}
