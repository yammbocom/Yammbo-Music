package it.fast4x.riplay.extensions.fastshare

import it.fast4x.riplay.extensions.yammboapi.AppEvents
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.net.Uri
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.BoxScope
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.compose.AsyncImage
import com.yambo.music.R
import it.fast4x.riplay.extensions.preferences.rememberPreference
import it.fast4x.riplay.ui.styling.semiBold
import it.fast4x.riplay.ui.styling.tight
import it.fast4x.riplay.utils.colorPalette
import it.fast4x.riplay.utils.typography
import kotlinx.coroutines.Dispatchers
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

/**
 * Looks offered in the preview, best first (the picker follows declaration order; the choice is
 * persisted by name, so reordering is safe). Light and Dark are quote cards on paper and black;
 * Cover and Gradient put big type on the artwork's dominant colour; Card floats a card of that
 * colour on black; Quote, Poster, Polaroid and Receipt are layouts of their own.
 */
enum class LyricsCardStyle { Cover, Card, Quote, Poster, Dark, Light, Gradient, Receipt, Polaroid }

private const val LYRICS_STYLE_KEY = "shareLyricsStyle"

/**
 * Sheet body for sharing lyrics, hosted by the global menu sheet. Step one picks the lines
 * (the one being sung is picked already); step two previews the image with a choice of
 * background and target before anything leaves the app.
 */
@Composable
fun ShareLyricsPicker(
    lines: List<ShareableLyricLine>,
    initialIndex: Int,
    title: String,
    artist: String,
    thumbnailUrl: String?,
    onDismiss: () -> Unit,
    // Link printed in the card footer and sent with it; null leaves both out.
    shareUrl: String? = null,
) {
    val context = LocalContext.current
    val selected = remember {
        mutableStateListOf<Int>().apply { if (lines.isNotEmpty()) add(initialIndex.coerceIn(0, lines.lastIndex)) }
    }
    var previewing by remember { mutableStateOf(false) }
    // The last look picked is remembered between openings.
    var savedStyle by rememberPreference(LYRICS_STYLE_KEY, LyricsCardStyle.Light.name)
    val style = LyricsCardStyle.entries.firstOrNull { it.name == savedStyle } ?: LyricsCardStyle.Light
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var isGenerating by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(previewing, style) {
        if (!previewing) return@LaunchedEffect
        isGenerating = true
        previewUri = LyricsCardGenerator.generate(
            context, selected.sorted().map { lines[it].text }, title, artist, thumbnailUrl, style, shareUrl
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
            style = typography().xl.tight.copy(
                color = colorPalette().text,
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp
            ),
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
                        // Solid white when picked, a hairline ring otherwise.
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .then(
                                    if (isSelected) Modifier.background(colorPalette().text)
                                    else Modifier.border(1.5.dp, colorPalette().text.copy(alpha = 0.3f), CircleShape)
                                )
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
                    .clip(RoundedCornerShape(18.dp))
                if (previewUri != null)
                    AsyncImage(
                        model = previewUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = frame
                    )
                else
                    Box(modifier = frame.background(colorPalette().background2))
            }

            // Scrolls sideways: a mini layout of every look, the chosen one outlined in white.
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                LyricsCardStyle.entries.forEach { option ->
                    StyleThumb(
                        label = stringResource(
                            when (option) {
                                LyricsCardStyle.Light -> R.string.lyrics_style_light
                                LyricsCardStyle.Dark -> R.string.lyrics_style_dark
                                LyricsCardStyle.Cover -> R.string.lyrics_style_cover
                                LyricsCardStyle.Gradient -> R.string.lyrics_style_gradient
                                LyricsCardStyle.Quote -> R.string.lyrics_style_quote
                                LyricsCardStyle.Poster -> R.string.lyrics_style_poster
                                LyricsCardStyle.Polaroid -> R.string.lyrics_style_polaroid
                                LyricsCardStyle.Receipt -> R.string.lyrics_style_receipt
                                LyricsCardStyle.Card -> R.string.lyrics_style_card
                            }
                        ),
                        selected = option == style,
                        onClick = { if (!isGenerating) savedStyle = option.name }
                    ) { LyricsStyleMini(option, thumbnailUrl) }
                }
            }

            // Round targets for the apps that are installed; the main button is the chooser.
            val canSend = previewUri != null && !isGenerating
            val send = { packageName: String? ->
                previewUri?.let { shareLyricsImage(context, it, title, artist, shareUrl, packageName) }
                onDismiss()
            }
            val installed = remember {
                listOf(
                    Triple(INSTAGRAM_PACKAGE, R.drawable.brand_instagram, "Instagram"),
                    Triple(WHATSAPP_PACKAGE, R.drawable.brand_whatsapp, "WhatsApp"),
                    Triple(X_PACKAGE, R.drawable.brand_x, "X"),
                ).filter { isPackageInstalled(context, it.first) }
            }
            if (installed.isNotEmpty())
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                        .alpha(if (canSend) 1f else 0.5f)
                        .horizontalScroll(rememberScrollState())
                ) {
                    installed.forEach { (pkg, icon, label) ->
                        ShareTarget(icon = icon, label = label) { if (canSend) send(pkg) }
                    }
                }

            PrimaryPill(
                text = stringResource(if (isGenerating) R.string.share_lyrics_working else R.string.share_sheet_title),
                enabled = canSend,
                onClick = { send(null) }
            )
            BasicText(
                text = stringResource(R.string.share_change_lines),
                style = typography().xs.semiBold.copy(
                    color = colorPalette().textSecondary,
                    textAlign = TextAlign.Center
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { previewing = false }
                    .padding(vertical = 10.dp)
            )
        }
    }
}

/** Tiny drawing of each lyrics layout, built from boxes so it needs no rendering. */
@Composable
private fun BoxScope.LyricsStyleMini(style: LyricsCardStyle, coverUrl: String?) {
    val white = ComposeColor.White
    val dark = ComposeColor(0xff222222)
    when (style) {
        LyricsCardStyle.Light -> {
            Box(Modifier.fillMaxSize().background(ComposeColor(0xffF2F2EE)))
            MiniBars(dark, Modifier.align(Alignment.Center).padding(horizontal = 6.dp), listOf(0.9f, 0.7f, 0.8f))
        }
        LyricsCardStyle.Dark -> {
            Box(Modifier.fillMaxSize().background(ComposeColor(0xff0A0A0A)))
            MiniBars(white, Modifier.align(Alignment.Center).padding(horizontal = 6.dp), listOf(0.9f, 0.7f, 0.8f))
        }
        LyricsCardStyle.Cover -> {
            MiniCover(coverUrl, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.35f)))
            MiniBars(white, Modifier.align(Alignment.Center).padding(horizontal = 6.dp), listOf(0.9f, 0.7f, 0.8f))
        }
        LyricsCardStyle.Card -> {
            Box(Modifier.fillMaxSize().background(ComposeColor.Black))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 40.dp, height = 56.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(ComposeColor(0xffE6E6E1))
            )
            MiniBars(dark, Modifier.align(Alignment.Center).offset(y = (-10).dp).padding(horizontal = 11.dp), listOf(0.9f, 0.7f, 0.8f))
            MiniCover(coverUrl, Modifier.align(Alignment.Center).offset(x = (-10).dp, y = 19.dp).size(8.dp).clip(RoundedCornerShape(2.dp)))
        }
        LyricsCardStyle.Gradient -> {
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(ComposeColor(0xff4A4A4A), ComposeColor(0xff0E0E0E)))
                )
            )
            MiniBars(white, Modifier.align(Alignment.Center).padding(horizontal = 6.dp), listOf(0.9f, 0.7f, 0.8f))
        }
        LyricsCardStyle.Quote -> {
            Box(Modifier.fillMaxSize().background(ComposeColor(0xff0A0A0A)))
            BasicText(
                text = "\u201C",
                style = typography().xl.copy(color = white.copy(alpha = 0.5f), fontSize = 54.sp, fontWeight = FontWeight.Bold),
                modifier = Modifier.align(Alignment.TopStart).offset(x = 3.dp, y = (-4).dp)
            )
            MiniBars(white, Modifier.align(Alignment.Center).padding(start = 5.dp, end = 5.dp, top = 8.dp), listOf(0.95f, 0.8f, 0.6f))
            MiniCover(coverUrl, Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 5.dp).size(9.dp).clip(CircleShape))
        }
        LyricsCardStyle.Poster -> {
            MiniCover(coverUrl, Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.45f)))
            MiniBars(white, Modifier.align(Alignment.Center).padding(horizontal = 8.dp), listOf(0.9f, 0.6f, 0.8f))
        }
        LyricsCardStyle.Polaroid -> {
            MiniCover(coverUrl, Modifier.fillMaxSize().alpha(0.5f))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .rotate(-4f)
                    .size(width = 34.dp, height = 52.dp)
                    .background(ComposeColor(0xffFAFAF7))
                    .padding(3.dp)
            ) {
                MiniCover(coverUrl, Modifier.fillMaxWidth().height(20.dp))
                MiniBars(dark, Modifier.align(Alignment.BottomStart).padding(bottom = 6.dp), listOf(0.9f, 0.7f))
            }
        }
        LyricsCardStyle.Receipt -> {
            Box(Modifier.fillMaxSize().background(ComposeColor(0xff0E0E0E)))
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(width = 32.dp, height = 58.dp)
                    .background(ComposeColor(0xffF7F7F3))
                    .padding(4.dp)
            ) {
                MiniBars(ComposeColor(0xff555555), Modifier.align(Alignment.TopStart), listOf(0.9f, 0.6f, 0.8f, 0.5f, 0.9f, 0.7f))
            }
        }
    }
}

@Composable
private fun PrimaryPill(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .padding(horizontal = 24.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(colorPalette().text)
            .alpha(if (enabled) 1f else 0.5f)
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        BasicText(
            text = text,
            style = typography().s.tight.copy(
                color = colorPalette().background0,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center
            )
        )
    }
}

/** Sends the card to [packageName] when it is installed, otherwise to the system chooser. */
private fun shareLyricsImage(
    context: Context,
    uri: Uri,
    title: String,
    artist: String,
    shareUrl: String?,
    packageName: String?
) {
    val label = if (artist.isBlank()) title else "$title - $artist"
    shareWithImage(
        context,
        packageName?.takeIf { isPackageInstalled(context, it) },
        uri,
        label,
        shareUrl.orEmpty()
    )
    AppEvents.log(AppEvents.SHARE_LYRICS, detail = label)
}

/**
 * Yammbo Noir lyrics card, 9:16. Every text sits on a background whose contrast was checked with
 * the helpers in [ShareImageGenerator] (ink chosen from the background, background darkened when
 * a ratio is missed, scrims and a veil over photos). Nothing important above y=250 or below y=1620;
 * side margins are 72 px. Colour only ever comes from the artwork.
 */
object LyricsCardGenerator {
    private const val WIDTH = 1080
    private const val HEIGHT = 1920
    private const val MARGIN = 72f
    private const val HEADER_Y = 280f
    private const val FOOTER_Y = 1580f
    private const val QUOTE_BASELINE = 640f
    private const val LINES_TOP = 580f
    private const val FOOTER_RULE_Y = 1450f
    private const val FOOTER_COVER = 120
    private const val FOOTER_COVER_TOP = 1490f
    private val INK_DARK = Color.parseColor("#0A0A0A")

    suspend fun generate(
        context: Context,
        lines: List<String>,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        style: LyricsCardStyle = LyricsCardStyle.Light,
        shareUrl: String? = null,
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val cover = ShareImageGenerator.loadCoverArt(thumbnailUrl)
            val palette = cover?.let { Palette.from(it).maximumColorCount(16).generate() }
            val (base, second) = pickColors(palette)
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            val host = ShareImageGenerator.hostOf(shareUrl.orEmpty())
            when (style) {
                LyricsCardStyle.Quote -> drawBigQuote(context, canvas, lines, title, artist, cover, base, host)
                LyricsCardStyle.Poster -> drawPoster(context, canvas, lines, title, artist, cover, base, second, host)
                LyricsCardStyle.Polaroid -> drawPolaroid(context, canvas, lines, title, artist, cover, base, second, host)
                LyricsCardStyle.Receipt -> drawReceipt(context, canvas, lines, title, artist, host)
                LyricsCardStyle.Card -> drawFloatingCard(context, canvas, lines, title, artist, cover, palette, host)
                else -> drawQuoteCard(context, canvas, lines, title, artist, cover, base, second, style, host)
            }

            cover?.recycle()
            ShareImageGenerator.saveBitmapAndGetUri(context, bitmap)
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    /**
     * Light, Dark, Cover and Gradient. Cover and Gradient are "big text on the dominant colour"
     * (700, 72-96 px, left aligned, up to 6 lines); Light and Dark keep the quote mark and, when
     * several lines are picked, show the first one at full ink and the rest at 35%.
     */
    private fun drawQuoteCard(
        context: Context,
        canvas: Canvas,
        lines: List<String>,
        title: String,
        artist: String,
        cover: Bitmap?,
        base: Int,
        second: Int,
        style: LyricsCardStyle,
        host: String,
    ) {
        val big = style == LyricsCardStyle.Cover || style == LyricsCardStyle.Gradient
        val frame = when (style) {
            LyricsCardStyle.Light -> Color.parseColor("#F2F2EE")
            LyricsCardStyle.Dark -> Color.parseColor("#0A0A0A")
            else -> base // Cover and Gradient: already a dark, contrast-checked dominant colour
        }
        val ink = ShareImageGenerator.inkFor(frame)
        val onLight = ink != Color.WHITE
        val inkSoft = ColorUtils.setAlphaComponent(ink, 178)

        if (style == LyricsCardStyle.Gradient)
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
                shader = LinearGradient(
                    0f, 0f, WIDTH.toFloat() * 0.4f, HEIGHT.toFloat(),
                    base, second, Shader.TileMode.CLAMP
                )
            })
        else canvas.drawColor(frame)

        val bold = ShareImageGenerator.geist(context, R.font.geist_w700)
        val mono = ShareImageGenerator.geist(context, R.font.geist_mono_w400)

        // Header: wordmark and the "lyrics" tag
        ShareImageGenerator.drawLogo(context, canvas, MARGIN, HEADER_Y, 42f, onLight)
        canvas.drawText(
            context.getString(R.string.share_lyrics_tag).uppercase(), WIDTH - MARGIN, HEADER_Y + 9f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = inkSoft
                textSize = 27f
                letterSpacing = 0.14f
                typeface = mono
                textAlign = Paint.Align.RIGHT
            }
        )

        val innerWidth = (WIDTH - MARGIN * 2).toInt()
        val raw = lines.joinToString("\n")
        val text: CharSequence = if (!big && lines.size > 1)
            SpannableStringBuilder(raw).apply {
                setSpan(
                    ForegroundColorSpan(ColorUtils.setAlphaComponent(ink, 89)),
                    lines.first().length + 1, raw.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        else raw

        if (big) {
            val room = FOOTER_RULE_Y - 70f - 400f
            val layout = fitQuote(
                text, sizeFor(raw.length, 96f, 88f, 80f, 72f), 48f, 4f, bold, ink, innerWidth, room,
                Layout.Alignment.ALIGN_NORMAL, 6
            )
            canvas.save()
            canvas.translate(MARGIN, 400f + (room - layout.height) / 2f)
            layout.draw(canvas)
            canvas.restore()
        } else {
            // Big quote mark
            canvas.drawText("“", MARGIN - 6f, QUOTE_BASELINE, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                textSize = 300f
                typeface = bold
            })
            val room = FOOTER_RULE_Y - 70f - LINES_TOP
            val layout = fitQuote(
                text, sizeFor(raw.length, 108f, 96f, 84f, 72f), 48f, 8f, bold, ink, innerWidth, room,
                Layout.Alignment.ALIGN_NORMAL, 8
            )
            canvas.save()
            canvas.translate(MARGIN, LINES_TOP)
            layout.draw(canvas)
            canvas.restore()
        }

        drawTrackFooter(context, canvas, cover, title, artist, host, ink, onLight)
    }

    /** Rule, 120 px cover, title and artist at 32 px and the host on the right. */
    private fun drawTrackFooter(
        context: Context, canvas: Canvas, cover: Bitmap?, title: String, artist: String, host: String,
        ink: Int, onLight: Boolean
    ) {
        val inkSoft = ColorUtils.setAlphaComponent(ink, 178)
        val bold = ShareImageGenerator.geist(context, R.font.geist_w700)
        val regular = ShareImageGenerator.geist(context, R.font.geist_w400)
        val mono = ShareImageGenerator.geist(context, R.font.geist_mono_w400)

        canvas.drawRect(MARGIN, FOOTER_RULE_Y, WIDTH - MARGIN, FOOTER_RULE_Y + 3f, Paint().apply {
            color = ColorUtils.setAlphaComponent(ink, 46)
        })
        val coverRect = RectF(
            MARGIN, FOOTER_COVER_TOP, MARGIN + FOOTER_COVER, FOOTER_COVER_TOP + FOOTER_COVER
        )
        if (cover != null) {
            ShareImageGenerator.drawCoverRect(context, canvas, cover, coverRect, 16f, 0)
        } else {
            canvas.drawRoundRect(coverRect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink })
            ShareImageGenerator.drawIcon(
                context, canvas, coverRect.centerX(), coverRect.centerY(), 72f,
                if (onLight) Color.WHITE else INK_DARK
            )
        }

        val centerY = coverRect.centerY()
        val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSoft
            textSize = 27f
            typeface = mono
            textAlign = Paint.Align.RIGHT
        }
        var urlWidth = 0f
        if (host.isNotBlank()) {
            val hostShown = ShareImageGenerator.ellipsizeText(host, urlPaint, 340f)
            canvas.drawText(hostShown, WIDTH - MARGIN, centerY + 10f, urlPaint)
            urlWidth = urlPaint.measureText(hostShown)
        }

        val textLeft = coverRect.right + 32f
        val textWidth = WIDTH - MARGIN - urlWidth - 30f - textLeft
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 32f; typeface = bold }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkSoft; textSize = 32f; typeface = regular }
        val titleY = if (artist.isBlank()) centerY + 11f else centerY - 6f
        canvas.drawText(ShareImageGenerator.ellipsizeText(title, titlePaint, textWidth), textLeft, titleY, titlePaint)
        if (artist.isNotBlank())
            canvas.drawText(ShareImageGenerator.ellipsizeText(artist, artistPaint, textWidth), textLeft, centerY + 38f, artistPaint)
    }

    /** Wordmark on the left, the "lyrics" tag on the right, for the dark layouts. */
    private fun drawDarkHeader(context: Context, canvas: Canvas) {
        ShareImageGenerator.drawLogo(context, canvas, MARGIN, HEADER_Y, 42f, false)
        canvas.drawText(
            context.getString(R.string.share_lyrics_tag).uppercase(), WIDTH - MARGIN, HEADER_Y + 9f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(204, 255, 255, 255)
                textSize = 27f
                letterSpacing = 0.14f
                typeface = ShareImageGenerator.geist(context, R.font.geist_mono_w400)
                textAlign = Paint.Align.RIGHT
            }
        )
    }

    /** The blurred cover under a veil; a plain gradient of the cover colours when there is none. */
    private fun drawBackdrop(canvas: Canvas, cover: Bitmap?, base: Int, second: Int) {
        if (cover != null) {
            ShareImageGenerator.drawBlurredBackdrop(canvas, cover, null)
            ShareImageGenerator.drawVeil(canvas)
        } else {
            canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
                shader = LinearGradient(
                    0f, 0f, WIDTH.toFloat() * 0.4f, HEIGHT.toFloat(), base, second, Shader.TileMode.CLAMP
                )
            })
        }
    }

    /**
     * The biggest size from [startSize] down to [minSize] at which the text fits in [room] and in
     * [maxLines]. If even the smallest does not fit it is cut with an ellipsis, never overflowed.
     */
    private fun fitQuote(
        text: CharSequence, startSize: Float, minSize: Float, step: Float, face: Typeface, ink: Int,
        width: Int, room: Float, align: Layout.Alignment, maxLines: Int, shadow: Boolean = false
    ): StaticLayout {
        var size = startSize
        var layout = quoteLayout(text, size, face, ink, width, align, 60, shadow)
        while ((layout.height > room || layout.lineCount > maxLines) && size > minSize) {
            size -= step
            layout = quoteLayout(text, size, face, ink, width, align, 60, shadow)
        }
        if (layout.height > room || layout.lineCount > maxLines) {
            val lineH = layout.height.toFloat() / layout.lineCount.coerceAtLeast(1)
            val allowed = minOf(maxLines, (room / lineH).toInt()).coerceAtLeast(1)
            layout = quoteLayout(text, size, face, ink, width, align, allowed, shadow)
        }
        return layout
    }

    private fun sizeFor(chars: Int, a: Float, b: Float, c: Float, d: Float): Float = when {
        chars <= 60 -> a
        chars <= 110 -> b
        chars <= 170 -> c
        else -> d
    }

    /** Huge quotation mark (dimmed, so it never fights the type) behind very large lines. */
    private fun drawBigQuote(
        context: Context, canvas: Canvas, lines: List<String>, title: String, artist: String,
        cover: Bitmap?, base: Int, host: String
    ) {
        canvas.drawColor(Color.parseColor("#0A0A0A"))
        val bold = ShareImageGenerator.geist(context, R.font.geist_w700)

        canvas.drawText("“", MARGIN - 30f, 1000f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (cover != null) ColorUtils.blendARGB(Color.parseColor("#0A0A0A"), base, 0.4f)
            else Color.parseColor("#262626")
            textSize = 1000f
            typeface = bold
        })
        drawDarkHeader(context, canvas)

        val text = lines.joinToString("\n")
        val layout = fitQuote(
            text, sizeFor(text.length, 128f, 112f, 96f, 80f), 52f, 8f, bold, Color.WHITE,
            (WIDTH - MARGIN * 2).toInt(), 880f, Layout.Alignment.ALIGN_NORMAL, 8
        )
        canvas.save()
        canvas.translate(MARGIN, 440f)
        layout.draw(canvas)
        canvas.restore()

        drawTrackFooter(context, canvas, cover, title, artist, host, Color.WHITE, false)
    }

    /** Blurred full-cover background under a 45%+ darkening, lines centred in white (84 px), the track below. */
    private fun drawPoster(
        context: Context, canvas: Canvas, lines: List<String>, title: String, artist: String,
        cover: Bitmap?, base: Int, second: Int, host: String
    ) {
        if (cover != null) ShareImageGenerator.drawBlurredBackdrop(canvas, cover, null)
        else drawBackdrop(canvas, null, base, second)
        val average = ShareImageGenerator.averageColor(cover, base)
        canvas.drawRect(0f, 0f, WIDTH.toFloat(), HEIGHT.toFloat(), Paint().apply {
            color = Color.argb(ShareImageGenerator.veilAlphaFor(average, Color.WHITE, 4.5, 0.45f), 0, 0, 0)
        })
        ShareImageGenerator.drawEdgeScrims(canvas)
        drawDarkHeader(context, canvas)
        val bold = ShareImageGenerator.geist(context, R.font.geist_w700)
        val regular = ShareImageGenerator.geist(context, R.font.geist_w400)
        val mono = ShareImageGenerator.geist(context, R.font.geist_mono_w400)

        val text = lines.joinToString("\n")
        val room = 800f
        val layout = ShareImageGenerator.shadowed(
            fitQuote(
                text, sizeFor(text.length, 84f, 80f, 72f, 64f), 44f, 4f, bold, Color.WHITE,
                (WIDTH - MARGIN * 2).toInt(), room, Layout.Alignment.ALIGN_CENTER, 8, true
            )
        )
        canvas.save()
        canvas.translate(MARGIN, 400f + (room - layout.height) / 2f)
        layout.draw(canvas)
        canvas.restore()

        ShareImageGenerator.drawCoverCircle(context, canvas, cover, WIDTH / 2f, 1290f, 62f)
        val centered = { face: Typeface, size: Float, alpha: Int ->
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(alpha, 255, 255, 255)
                textSize = size
                typeface = face
                textAlign = Paint.Align.CENTER
            }
        }
        val maxWidth = WIDTH - MARGIN * 2
        val titlePaint = ShareImageGenerator.softShadow(centered(bold, 44f, 255))
        canvas.drawText(ShareImageGenerator.ellipsizeText(title, titlePaint, maxWidth), WIDTH / 2f, 1425f, titlePaint)
        if (artist.isNotBlank()) {
            val artistPaint = centered(regular, 34f, 217)
            canvas.drawText(ShareImageGenerator.ellipsizeText(artist, artistPaint, maxWidth), WIDTH / 2f, 1475f, artistPaint)
        }
        if (host.isNotBlank())
            canvas.drawText(host, WIDTH / 2f, 1545f, centered(mono, 28f, 204))
    }

    /** A tilted instant-photo frame: the cover on top, the lines in its white margin. */
    private fun drawPolaroid(
        context: Context, canvas: Canvas, lines: List<String>, title: String, artist: String,
        cover: Bitmap?, base: Int, second: Int, host: String
    ) {
        drawBackdrop(canvas, cover, base, second)
        ShareImageGenerator.drawEdgeScrims(canvas)
        drawDarkHeader(context, canvas)
        val bold = ShareImageGenerator.geist(context, R.font.geist_w700)
        val regular = ShareImageGenerator.geist(context, R.font.geist_w400)

        val frame = RectF(130f, 370f, 950f, 1500f)
        canvas.save()
        canvas.rotate(-3f, 540f, 935f)
        canvas.drawRoundRect(frame, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FAFAF7")
            setShadowLayer(70f, 0f, 30f, Color.argb(150, 0, 0, 0))
        })
        val photo = RectF(frame.left + 48f, frame.top + 48f, frame.right - 48f, frame.top + 48f + 480f)
        ShareImageGenerator.drawCoverRect(context, canvas, cover, photo, 6f, 0)

        val text = lines.joinToString("\n")
        val layout = fitQuote(
            text, sizeFor(text.length, 72f, 62f, 52f, 44f), 28f, 4f, bold, Color.parseColor("#111111"),
            photo.width().toInt(), 380f, Layout.Alignment.ALIGN_NORMAL, 8
        )
        canvas.save()
        canvas.translate(photo.left, photo.bottom + 56f)
        layout.draw(canvas)
        canvas.restore()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111"); textSize = 40f; textSkewX = -0.12f; typeface = bold
        }
        canvas.drawText(ShareImageGenerator.ellipsizeText(title, titlePaint, photo.width()), photo.left, frame.bottom - 92f, titlePaint)
        if (artist.isNotBlank()) {
            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#555555"); textSize = 32f; textSkewX = -0.12f; typeface = regular
            }
            canvas.drawText(ShareImageGenerator.ellipsizeText(artist, artistPaint, photo.width()), photo.left, frame.bottom - 44f, artistPaint)
        }
        canvas.save()
        canvas.rotate(3f, 540f, frame.top)
        canvas.drawRect(440f, frame.top - 34f, 640f, frame.top + 34f, Paint().apply {
            color = Color.argb(120, 255, 255, 255)
        })
        canvas.restore()
        canvas.restore()

        ShareImageGenerator.drawFooter(context, canvas, host, FOOTER_Y)
    }

    /** A till receipt: the lines in mono capitals, dashed rules, track and artist, a barcode. */
    private fun drawReceipt(
        context: Context, canvas: Canvas, lines: List<String>, title: String, artist: String, host: String
    ) {
        canvas.drawColor(Color.parseColor("#0E0E0E"))
        drawDarkHeader(context, canvas)
        val mono = ShareImageGenerator.geist(context, R.font.geist_mono_w400)
        val ink = Color.parseColor("#111111")
        val gray = Color.parseColor("#5C5C58")

        val left = 130f
        val right = 950f
        val top = 350f
        ShareImageGenerator.drawReceiptPaper(canvas, left, top, right, 1590f)
        val textLeft = left + 60f
        val textRight = right - 60f
        val width = (textRight - textLeft).toInt()
        val centre = (left + right) / 2f

        canvas.drawText("YAMMBO MUSIC", centre, top + 92f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink; textSize = 46f; letterSpacing = 0.12f; typeface = mono
            isFakeBoldText = true; textAlign = Paint.Align.CENTER
        })
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray; textSize = 26f; letterSpacing = 0.14f; typeface = mono; textAlign = Paint.Align.CENTER
        }
        canvas.drawText(context.getString(R.string.share_lyrics_tag).uppercase(), centre, top + 138f, sub)
        ShareImageGenerator.drawDashed(canvas, textLeft, textRight, top + 176f, ink)

        val text = lines.joinToString("\n").uppercase()
        var size = sizeFor(text.length, 46f, 40f, 34f, 30f)
        var layout = ShareImageGenerator.monoLayout(text, size, mono, ink, width, 14, false)
        while (layout.height > 600f && size > 22f) {
            size -= 2f
            layout = ShareImageGenerator.monoLayout(text, size, mono, ink, width, 14, false)
        }
        if (layout.height > 600f) {
            val lineH = layout.height.toFloat() / layout.lineCount.coerceAtLeast(1)
            layout = ShareImageGenerator.monoLayout(text, size, mono, ink, width, (600f / lineH).toInt().coerceAtLeast(1), false)
        }
        canvas.save()
        canvas.translate(textLeft, top + 212f)
        layout.draw(canvas)
        canvas.restore()

        ShareImageGenerator.drawDashed(canvas, textLeft, textRight, 1190f, ink)
        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray; textSize = 24f; letterSpacing = 0.14f; typeface = mono
        }
        var y = 1250f
        fun field(name: String, value: String) {
            canvas.drawText(name.uppercase(), textLeft, y, labelPaint)
            val l = ShareImageGenerator.monoLayout(value, 38f, mono, ink, width, 1, true)
            canvas.save()
            canvas.translate(textLeft, y + 16f)
            l.draw(canvas)
            canvas.restore()
            y += 16f + l.height + 48f
        }
        field(context.getString(R.string.share_receipt_track), title)
        if (artist.isNotBlank()) field(context.getString(R.string.share_receipt_artist), artist)

        ShareImageGenerator.drawBarcode(canvas, textLeft, textRight, 1455f, 1515f, (title + artist).hashCode().toLong(), ink)
        if (host.isNotBlank())
            canvas.drawText(host, centre, 1560f, Paint(sub).apply { color = ink; textSize = 28f; letterSpacing = 0.08f })
    }

    /**
     * Floating card: black frame, a card of the cover's dominant colour (ink picked from it,
     * colour darkened or lightened until the text reads) holding the lines, with a 96 px cover and
     * the track in its corner. The card grows with the text between y=400 and y=1500.
     */
    private fun drawFloatingCard(
        context: Context, canvas: Canvas, lines: List<String>, title: String, artist: String,
        cover: Bitmap?, palette: Palette?, host: String
    ) {
        canvas.drawColor(Color.BLACK)
        drawDarkHeader(context, canvas)
        val bold = ShareImageGenerator.geist(context, R.font.geist_w700)
        val regular = ShareImageGenerator.geist(context, R.font.geist_w400)

        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(ShareImageGenerator.dominantOf(palette, Color.parseColor("#EDEDE8")), hsl)
        hsl[1] = minOf(hsl[1], 0.65f)
        var bg = ColorUtils.HSLToColor(hsl)
        val ink = ShareImageGenerator.inkFor(bg)
        bg = ShareImageGenerator.ensureContrast(bg, ink, 4.5)
        val inkSoft = ColorUtils.setAlphaComponent(ink, 178)

        val cardLeft = 72f
        val cardRight = WIDTH - 72f
        val pad = 64f
        val innerWidth = (cardRight - cardLeft - pad * 2).toInt()
        val footerH = 96f
        val text = lines.joinToString("\n")
        val room = 1100f - (pad + 56f + footerH + pad)
        val layout = fitQuote(
            text, sizeFor(text.length, 84f, 76f, 68f, 60f), 44f, 4f, bold, ink, innerWidth, room,
            Layout.Alignment.ALIGN_NORMAL, 8
        )
        val cardH = (pad + layout.height + 56f + footerH + pad).coerceIn(560f, 1100f)
        val cardTop = (950f - cardH / 2f).coerceIn(400f, 1500f - cardH)
        val card = RectF(cardLeft, cardTop, cardRight, cardTop + cardH)
        canvas.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })

        canvas.save()
        canvas.translate(cardLeft + pad, cardTop + pad)
        layout.draw(canvas)
        canvas.restore()

        val footerTop = card.bottom - pad - footerH
        val coverRect = RectF(cardLeft + pad, footerTop, cardLeft + pad + footerH, footerTop + footerH)
        if (cover != null) {
            ShareImageGenerator.drawCoverRect(context, canvas, cover, coverRect, 16f, 0)
        } else {
            canvas.drawRoundRect(coverRect, 16f, 16f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink })
            ShareImageGenerator.drawIcon(
                context, canvas, coverRect.centerX(), coverRect.centerY(), 60f,
                if (ink == Color.WHITE) INK_DARK else Color.WHITE
            )
        }
        val textLeft = coverRect.right + 28f
        val textWidth = cardRight - pad - textLeft
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink; textSize = 32f; typeface = bold }
        val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = inkSoft; textSize = 28f; typeface = regular }
        val titleY = if (artist.isBlank()) coverRect.centerY() + 11f else coverRect.centerY() - 8f
        canvas.drawText(ShareImageGenerator.ellipsizeText(title, titlePaint, textWidth), textLeft, titleY, titlePaint)
        if (artist.isNotBlank())
            canvas.drawText(ShareImageGenerator.ellipsizeText(artist, artistPaint, textWidth), textLeft, coverRect.centerY() + 32f, artistPaint)

        ShareImageGenerator.drawFooter(context, canvas, host, FOOTER_Y)
    }

    private fun quoteLayout(
        text: CharSequence, size: Float, face: Typeface, ink: Int, width: Int,
        align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL,
        maxLines: Int = 60,
        shadow: Boolean = false
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = size
            typeface = face
            letterSpacing = -0.035f
            if (shadow) setShadowLayer(24f, 0f, 2f, Color.argb(90, 0, 0, 0))
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align)
            .setLineSpacing(0f, 1.08f)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    /**
     * Two colours from the cover for the frame: a rich dark one (saturation <= 60%, lightness
     * 15-30%, darkened further until white reads at 4.5:1) and a deeper one for the far end of the
     * gradient. Without artwork both are neutral greys.
     */
    private fun pickColors(palette: Palette?): Pair<Int, Int> {
        if (palette == null) return Color.rgb(40, 40, 40) to Color.rgb(20, 20, 20)
        val main = palette.vibrantSwatch ?: palette.mutedSwatch ?: palette.dominantSwatch
        val deep = palette.darkVibrantSwatch ?: palette.darkMutedSwatch ?: palette.dominantSwatch
        val base = ShareImageGenerator.ensureContrast(
            ShareImageGenerator.darkBackground(main?.rgb ?: Color.DKGRAY), Color.WHITE, 4.5
        )
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(deep?.rgb ?: Color.BLACK, hsl)
        hsl[1] = minOf(hsl[1], 0.60f)
        hsl[2] = hsl[2].coerceIn(0.06f, 0.15f)
        return base to ColorUtils.HSLToColor(hsl)
    }
}
