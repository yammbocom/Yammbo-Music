package it.fast4x.riplay.extensions.fastshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.FontRes
import androidx.core.content.FileProvider
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.yambo.music.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Random

/**
 * Story card looks, best first (the picker follows declaration order; the choice is persisted by
 * name, so reordering is safe). Cover, Night, Paper and Grain share one layout (big cover, title,
 * progress bar, link box) and only change the background; the rest are layouts of their own.
 * Cover is the default: the artwork's dominant colour, darkened.
 */
enum class ShareCardStyle { Cover, Glass, Poster, Vinyl, Minimal, Receipt, Polaroid, Night, Paper, Grain }

/**
 * Yammbo Noir story card, 9:16. Colour only ever comes from the artwork, and every text sits on a
 * background whose contrast was checked (see [inkFor], [ensureContrast], [veilAlphaFor]).
 * Nothing important sits above y=250 or below y=1620 (Instagram draws its own UI there) and the
 * side margins are at least 72 px.
 */
object ShareImageGenerator {

    private const val IMAGE_WIDTH = 1080
    private const val IMAGE_HEIGHT = 1920

    private const val MARGIN = 96f
    private const val HEADER_CENTER_Y = 280f
    private const val FOOTER_Y = 1580f
    private const val COVER_TOP = 330f
    private const val COVER_SIZE = 800
    private const val LINK_BOX_BOTTOM = 1620f
    private const val LINK_BOX_HEIGHT = 136f

    private const val PAPER = "#F2F2EE"
    private val INK_DARK = Color.parseColor("#0A0A0A")

    suspend fun generateShareImage(
        context: Context,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        shareUrl: String,
        // Caption in the top-right pill; defaults to "now playing" (songs). Albums, artists,
        // playlists and podcasts pass their own so the card does not claim they are playing.
        headerLabel: String? = null,
        style: ShareCardStyle = ShareCardStyle.Cover,
        // "3:42": when known, the decorative progress bar prints times; otherwise it stays bare.
        durationText: String? = null
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val coverBitmap = loadCoverArt(thumbnailUrl)
            val palette = coverBitmap?.let {
                Palette.from(it).maximumColorCount(16).generate()
            }

            val host = hostOf(shareUrl)
            val label = headerLabel ?: context.getString(R.string.share_story_now_playing)
            val seconds = parseDurationSeconds(durationText)

            when (style) {
                ShareCardStyle.Vinyl -> drawVinyl(context, canvas, coverBitmap, palette, title, artist, host, label)
                ShareCardStyle.Poster -> drawPoster(context, canvas, coverBitmap, title, artist, host, label)
                ShareCardStyle.Polaroid -> drawPolaroid(context, canvas, coverBitmap, palette, title, artist, host, label)
                ShareCardStyle.Minimal -> drawMinimal(context, canvas, coverBitmap, palette, title, artist, host, label)
                ShareCardStyle.Receipt -> drawReceipt(context, canvas, coverBitmap, palette, title, artist, host, label)
                ShareCardStyle.Glass -> drawGlass(context, canvas, coverBitmap, palette, title, artist, host, label, seconds)
                else -> drawClassic(context, canvas, style, coverBitmap, palette, title, artist, host, label, seconds)
            }

            coverBitmap?.recycle()
            saveBitmapAndGetUri(context, bitmap)
        } catch (e: Exception) {
            null
        } catch (e: OutOfMemoryError) {
            // A 1080x1920 card on a low-RAM phone: fall back to sharing the plain link.
            null
        }
    }

    /** Host only ("music.yammbo.com"): the card never prints the long link, it would be cut. */
    internal fun hostOf(url: String): String =
        url.removePrefix("https://").removePrefix("http://").substringBefore('/')

    // ---------------------------------------------------------------------------------------
    // Legibility helpers, shared with the lyrics cards
    // ---------------------------------------------------------------------------------------

    /** WCAG relative luminance, 0 (black) to 1 (white). */
    internal fun luminance(color: Int): Double = ColorUtils.calculateLuminance(color)

    /** WCAG contrast ratio between two opaque colours, 1 to 21. */
    internal fun contrastRatio(a: Int, b: Int): Double {
        val x = luminance(a)
        val y = luminance(b)
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    /** White on dark backgrounds, near-black on light ones (switch point L = 0.179, ratio 4.58 both ways). */
    internal fun inkFor(background: Int): Int =
        if (luminance(background) < 0.179) Color.WHITE else INK_DARK

    /**
     * Moves the BACKGROUND (never the ink) in lightness until [ink] reads on it at [minRatio]:
     * 4.5 for body text, 3.0 is enough for text of 56 px and up.
     */
    internal fun ensureContrast(background: Int, ink: Int, minRatio: Double): Int {
        if (contrastRatio(background, ink) >= minRatio) return background
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(background, hsl)
        val step = if (luminance(ink) > 0.5) -0.02f else 0.02f
        var out = background
        var n = 0
        while (contrastRatio(out, ink) < minRatio && n < 60) {
            hsl[2] = (hsl[2] + step).coerceIn(0f, 1f)
            out = ColorUtils.HSLToColor(hsl)
            n++
        }
        return out
    }

    /** Noir dark background from any colour: saturation at most 60%, lightness 15-30%. */
    internal fun darkBackground(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = minOf(hsl[1], 0.60f)
        hsl[2] = hsl[2].coerceIn(0.15f, 0.30f)
        return ColorUtils.HSLToColor(hsl)
    }

    /** Light pastel background from any colour, for the layouts that use black ink. */
    internal fun pastelBackground(color: Int): Int {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color, hsl)
        hsl[1] = minOf(hsl[1], 0.45f)
        hsl[2] = hsl[2].coerceIn(0.82f, 0.90f)
        return ColorUtils.HSLToColor(hsl)
    }

    /** The cover's dominant colour, or [fallback] without artwork. */
    internal fun dominantOf(palette: Palette?, fallback: Int = Color.parseColor("#1A1A1A")): Int =
        palette?.getDominantColor(fallback) ?: fallback

    /** Mean colour of the artwork (16x16 sample), [fallback] without artwork. */
    internal fun averageColor(bmp: Bitmap?, fallback: Int): Int {
        if (bmp == null || bmp.isRecycled) return fallback
        val small = Bitmap.createScaledBitmap(bmp, 16, 16, true)
        var r = 0L
        var g = 0L
        var b = 0L
        val px = IntArray(16 * 16)
        small.getPixels(px, 0, 16, 0, 0, 16, 16)
        for (c in px) {
            r += Color.red(c)
            g += Color.green(c)
            b += Color.blue(c)
        }
        if (small !== bmp) small.recycle()
        val n = px.size
        return Color.rgb((r / n).toInt(), (g / n).toInt(), (b / n).toInt())
    }

    /**
     * Alpha (0-255) of a uniform black veil over an image whose mean colour is [background] so
     * that [ink] reads on it at [minRatio]; never below [start].
     */
    internal fun veilAlphaFor(background: Int, ink: Int = Color.WHITE, minRatio: Double = 4.5, start: Float = 0.45f): Int {
        var a = start
        while (a < 0.9f && contrastRatio(ink, ColorUtils.blendARGB(background, Color.BLACK, a)) < minRatio) a += 0.05f
        return (a * 255f).toInt().coerceIn(0, 255)
    }

    /** Vertical black scrim between [top] and [bottom], alpha 0-255 from [fromAlpha] to [toAlpha]. */
    internal fun drawScrim(canvas: Canvas, top: Float, bottom: Float, fromAlpha: Int, toAlpha: Int) {
        canvas.drawRect(0f, top, IMAGE_WIDTH.toFloat(), bottom, Paint().apply {
            shader = LinearGradient(
                0f, top, 0f, bottom, Color.argb(fromAlpha, 0, 0, 0), Color.argb(toAlpha, 0, 0, 0),
                Shader.TileMode.CLAMP
            )
        })
    }

    /** Dark bands behind the header and the footer of the layouts built on a photo. */
    internal fun drawEdgeScrims(canvas: Canvas) {
        drawScrim(canvas, 0f, 460f, 150, 0)
        drawScrim(canvas, 1400f, IMAGE_HEIGHT.toFloat(), 0, 170)
    }

    /** Soft reinforcement shadow (0, 2, 24 px, ~35% black) for text drawn over an image. */
    internal fun shadowed(layout: StaticLayout): StaticLayout {
        layout.paint.setShadowLayer(24f, 0f, 2f, Color.argb(90, 0, 0, 0))
        return layout
    }

    internal fun softShadow(paint: Paint): Paint {
        paint.setShadowLayer(24f, 0f, 2f, Color.argb(90, 0, 0, 0))
        return paint
    }

    private fun parseDurationSeconds(text: String?): Int? {
        if (text.isNullOrBlank()) return null
        val parts = text.trim().split(':').map { it.trim().toIntOrNull() ?: return null }
        val total = when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> return null
        }
        return total.takeIf { it > 0 }
    }

    private fun formatClock(seconds: Int): String =
        if (seconds >= 3600) String.format(Locale.US, "%d:%02d:%02d", seconds / 3600, seconds % 3600 / 60, seconds % 60)
        else String.format(Locale.US, "%d:%02d", seconds / 60, seconds % 60)

    /**
     * Decorative progress bar (about 35% filled) between [left] and [right], 6 px tall at [top],
     * with mono times underneath when the real duration is known.
     */
    private fun drawProgress(
        context: Context, canvas: Canvas, left: Float, right: Float, top: Float,
        ink: Int, seconds: Int?
    ) {
        val fraction = 0.35f
        canvas.drawRoundRect(RectF(left, top, right, top + 6f), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(ink, 90)
        })
        val filled = left + (right - left) * fraction
        canvas.drawRoundRect(RectF(left, top, filled, top + 6f), 3f, 3f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
        })
        canvas.drawCircle(filled, top + 3f, 9f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ink })
        if (seconds == null) return
        val mono = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(ink, 178)
            textSize = 26f
            typeface = geist(context, R.font.geist_mono_w400)
        }
        val baseline = top + 6f + 14f + 24f
        canvas.drawText(formatClock((seconds * fraction).toInt()), left, baseline, mono)
        mono.textAlign = Paint.Align.RIGHT
        canvas.drawText(formatClock(seconds), right, baseline, mono)
    }

    // ---------------------------------------------------------------------------------------
    // Layouts
    // ---------------------------------------------------------------------------------------

    /**
     * Cover, Night, Paper and Grain: big sharp cover, title, artist, a decorative progress bar and
     * the link box. Cover paints the dominant colour, darkened, fading to black.
     */
    private fun drawClassic(
        context: Context,
        canvas: Canvas,
        style: ShareCardStyle,
        coverBitmap: Bitmap?,
        palette: Palette?,
        title: String,
        artist: String,
        host: String,
        label: String,
        seconds: Int?
    ) {
        val onLight = style == ShareCardStyle.Paper
        val ink = if (onLight) INK_DARK else Color.WHITE
        val inkSoft = if (onLight) Color.argb(178, 10, 10, 10) else Color.argb(178, 255, 255, 255)

        when (style) {
            ShareCardStyle.Night -> {
                canvas.drawColor(Color.BLACK)
                drawCoverGlowBackdrop(canvas, palette)
            }
            ShareCardStyle.Paper -> canvas.drawColor(Color.parseColor(PAPER))
            ShareCardStyle.Grain -> drawGrain(canvas)
            else -> {
                val top = ensureContrast(darkBackground(dominantOf(palette)), Color.WHITE, 4.5)
                canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), Paint().apply {
                    shader = LinearGradient(
                        0f, 0f, 0f, IMAGE_HEIGHT.toFloat(),
                        intArrayOf(top, ColorUtils.blendARGB(top, Color.BLACK, 0.55f), Color.BLACK),
                        floatArrayOf(0f, 0.55f, 1f),
                        Shader.TileMode.CLAMP
                    )
                })
            }
        }

        drawHeader(context, canvas, label, ink, onLight)

        val semi = geist(context, R.font.geist_w600)
        val regular = geist(context, R.font.geist_w400)
        val timesH = if (seconds != null) 44f else 0f
        fun titleLayout(w: Int) = textLayout(title, 56f, semi, ink, w, 2, -0.02f)
        fun artistLayout(w: Int) =
            if (artist.isBlank()) null else textLayout(artist, 36f, regular, inkSoft, w, 1, 0f)
        fun belowCover(t: StaticLayout, a: StaticLayout?) =
            44f + t.height + (if (a != null) 12f + a.height else 0f) + 36f + 6f + timesH

        // The cover shrinks (never below 640) when a two-line title needs the room.
        var size = 864
        var t = titleLayout(size)
        var a = artistLayout(size)
        val limit = LINK_BOX_BOTTOM - LINK_BOX_HEIGHT - 36f
        val fit = (limit - COVER_TOP - belowCover(t, a)).toInt().coerceIn(640, 864)
        if (fit < size) {
            size = fit
            t = titleLayout(size)
            a = artistLayout(size)
        }
        val left = (IMAGE_WIDTH - size) / 2f
        val rect = RectF(left, COVER_TOP, left + size, COVER_TOP + size)
        drawCoverRect(context, canvas, coverBitmap, rect, 28f, if (onLight) 70 else 140)

        var y = rect.bottom + 44f
        canvas.save()
        canvas.translate(left, y)
        t.draw(canvas)
        canvas.restore()
        y += t.height
        if (a != null) {
            canvas.save()
            canvas.translate(left, y + 12f)
            a.draw(canvas)
            canvas.restore()
            y += 12f + a.height
        }
        drawProgress(context, canvas, left, left + size, y + 36f, ink, seconds)
        drawLinkBox(context, canvas, host, ink, inkSoft, onLight, rect.left, rect.right)
    }

    /** Wordmark on the left, caption pill on the right. */
    private fun drawHeader(context: Context, canvas: Canvas, label: String, ink: Int, onLight: Boolean) {
        drawLogo(context, canvas, MARGIN, HEADER_CENTER_Y, 42f, onLight)
        drawPill(context, canvas, label, IMAGE_WIDTH - MARGIN, HEADER_CENTER_Y, ink, onLight)
    }

    /**
     * Small footer for the layouts without a link box: a call to action on the left, host on the
     * right. [withLogo] swaps the call to action for the wordmark, only for layouts that draw no
     * wordmark anywhere else.
     */
    internal fun drawFooter(
        context: Context, canvas: Canvas, host: String, centerY: Float,
        ink: Int = Color.WHITE, onLight: Boolean = false, withLogo: Boolean = false
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ColorUtils.setAlphaComponent(ink, 204)
            textSize = 30f
            typeface = geist(context, R.font.geist_mono_w400)
            textAlign = Paint.Align.RIGHT
        }
        var hostWidth = 0f
        if (host.isNotBlank()) {
            hostWidth = paint.measureText(host)
            canvas.drawText(host, IMAGE_WIDTH - MARGIN, centerY + 10f, paint)
        }
        if (withLogo) {
            drawLogo(context, canvas, MARGIN, centerY, 38f, onLight)
            return
        }
        val cta = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 32f
            typeface = geist(context, R.font.geist_w600)
        }
        val gap = if (hostWidth > 0f) 32f else 0f
        val available = (IMAGE_WIDTH - 2 * MARGIN) - hostWidth - gap
        canvas.drawText(
            ellipsizeText(context.getString(R.string.share_footer_cta), cta, available),
            MARGIN, centerY + 10f, cta
        )
    }

    /**
     * Frosted glass: the cover blurred hard and zoomed 1.3x as the backdrop, a translucent card
     * in the middle holding a sharp cover, title, artist and the progress bar.
     */
    private fun drawGlass(
        context: Context, canvas: Canvas, cover: Bitmap?, palette: Palette?,
        title: String, artist: String, host: String, label: String, seconds: Int?
    ) {
        canvas.save()
        canvas.scale(1.3f, 1.3f, IMAGE_WIDTH / 2f, IMAGE_HEIGHT / 2f)
        drawBlurredBackdrop(canvas, cover, palette)
        canvas.restore()
        // Dark veil sized so white reads on the card (the 12% white fill lifts the backdrop).
        val average = averageColor(cover, dominantOf(palette))
        val veil = veilAlphaFor(ColorUtils.blendARGB(average, Color.WHITE, 0.12f), Color.WHITE, 4.5, 0.5f)
        canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), Paint().apply {
            color = Color.argb(veil, 0, 0, 0)
        })
        drawEdgeScrims(canvas)
        drawHeader(context, canvas, label, Color.WHITE, false)

        val semi = geist(context, R.font.geist_w600)
        val regular = geist(context, R.font.geist_w400)
        val textWidth = 760
        val t = textLayout(title, 56f, semi, Color.WHITE, textWidth, 2, -0.02f)
        val a = if (artist.isBlank()) null
        else textLayout(artist, 36f, regular, Color.argb(178, 255, 255, 255), textWidth, 1, 0f)
        val timesH = if (seconds != null) 44f else 0f
        val fixed = 64f + 36f + t.height + (if (a != null) 12f + a.height else 0f) + 34f + 6f + timesH + 56f
        val coverSize = (1180f - fixed).coerceIn(560f, 760f)
        val cardH = fixed + coverSize
        val cardLeft = 72f
        val cardTop = ((IMAGE_HEIGHT - cardH) / 2f - 20f).coerceAtLeast(340f)
        val card = RectF(cardLeft, cardTop, IMAGE_WIDTH - cardLeft, cardTop + cardH)
        canvas.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(31, 255, 255, 255)
        })
        canvas.drawRoundRect(card, 40f, 40f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.argb(51, 255, 255, 255)
        })

        val innerLeft = cardLeft + 88f
        val coverRect = RectF(innerLeft, cardTop + 64f, innerLeft + coverSize, cardTop + 64f + coverSize)
        drawCoverRect(context, canvas, cover, coverRect, 24f, 120)
        var y = coverRect.bottom + 36f
        canvas.save()
        canvas.translate(innerLeft, y)
        t.draw(canvas)
        canvas.restore()
        y += t.height
        if (a != null) {
            canvas.save()
            canvas.translate(innerLeft, y + 12f)
            a.draw(canvas)
            canvas.restore()
            y += 12f + a.height
        }
        drawProgress(context, canvas, innerLeft, innerLeft + textWidth, y + 34f, Color.WHITE, seconds)
        drawFooter(context, canvas, host, FOOTER_Y)
    }

    /** A record peeks out from behind the square cover, on a light pastel of the cover's colour. */
    private fun drawVinyl(
        context: Context, canvas: Canvas, cover: Bitmap?, palette: Palette?,
        title: String, artist: String, host: String, label: String
    ) {
        val bg = ensureContrast(pastelBackground(dominantOf(palette, Color.parseColor("#E6E6E1"))), INK_DARK, 7.0)
        val ink = INK_DARK
        canvas.drawColor(bg)
        drawHeader(context, canvas, label, ink, true)

        val size = 600f
        val coverRect = RectF(MARGIN, 400f, MARGIN + size, 400f + size)
        val cx = coverRect.centerX() + 270f
        val cy = coverRect.centerY()
        val radius = size / 2f

        // Disc body with a soft shadow
        canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#0B0B0B")
            setShadowLayer(50f, 0f, 20f, Color.argb(110, 0, 0, 0))
        })
        // Grooves: 1 px rings at about 8% white
        val groove = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1f
            color = Color.argb(20, 255, 255, 255)
        }
        var r = 125f
        while (r < radius - 6f) {
            canvas.drawCircle(cx, cy, r, groove)
            r += 8f
        }
        // Light sheen sweeping across the disc
        canvas.drawCircle(cx, cy, radius - 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = SweepGradient(
                cx, cy,
                intArrayOf(0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF, 0x26FFFFFF, 0x00FFFFFF),
                floatArrayOf(0f, 0.25f, 0.5f, 0.75f, 1f)
            )
        })
        // Centre label: the cover itself, 220 px round, then the spindle hole
        drawCoverCircle(context, canvas, cover, cx, cy, 110f)
        canvas.drawCircle(cx, cy, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg })

        drawCoverRect(context, canvas, cover, coverRect, 24f, 90)
        drawTextBlock(
            canvas, title, artist, coverRect.bottom + 90f, ink, Color.argb(178, 10, 10, 10),
            (IMAGE_WIDTH - MARGIN * 2).toInt(), context
        )
        drawFooter(context, canvas, host, FOOTER_Y, ink, true)
    }

    /** Full-bleed cover on top, huge title over a long black scrim. */
    private fun drawPoster(
        context: Context, canvas: Canvas, cover: Bitmap?,
        title: String, artist: String, host: String, label: String
    ) {
        canvas.drawColor(Color.BLACK)
        val photo = RectF(0f, 0f, IMAGE_WIDTH.toFloat(), 1400f)
        drawCoverRect(context, canvas, cover, photo, 0f, 0)
        // 0 -> 85% black from y=900, then solid so the photo's edge never shows
        canvas.drawRect(0f, 900f, IMAGE_WIDTH.toFloat(), 1400f, Paint().apply {
            shader = LinearGradient(
                0f, 900f, 0f, 1400f,
                intArrayOf(Color.argb(0, 0, 0, 0), Color.argb(217, 0, 0, 0), Color.BLACK),
                floatArrayOf(0f, 0.8f, 1f),
                Shader.TileMode.CLAMP
            )
        })
        drawScrim(canvas, 0f, 460f, 150, 0)
        drawHeader(context, canvas, label, Color.WHITE, false)

        val width = (IMAGE_WIDTH - MARGIN * 2).toInt()
        val bold = geist(context, R.font.geist_w700)
        val layout = shadowed(
            fitLayout(title, listOf(140f, 120f, 100f, 84f), bold, Color.WHITE, width, 3, -0.045f, 0.9f)
        )
        val titleBottom = 1500f
        canvas.save()
        canvas.translate(MARGIN, maxOf(1100f, titleBottom - layout.height))
        layout.draw(canvas)
        canvas.restore()

        if (artist.isNotBlank()) {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.argb(217, 255, 255, 255)
                textSize = 34f
                letterSpacing = 0.14f
                typeface = geist(context, R.font.geist_mono_w400)
            }
            canvas.drawText(ellipsizeText(artist.uppercase(), paint, width.toFloat()), MARGIN, 1552f, paint)
        }
        drawFooter(context, canvas, host, FOOTER_Y)
    }

    /** A tilted instant-photo frame over the blurred cover, the title in the white margin. */
    private fun drawPolaroid(
        context: Context, canvas: Canvas, cover: Bitmap?, palette: Palette?,
        title: String, artist: String, host: String, label: String
    ) {
        drawBlurredBackdrop(canvas, cover, palette)
        drawVeil(canvas)
        drawEdgeScrims(canvas)
        drawHeader(context, canvas, label, Color.WHITE, false)

        val frame = RectF(130f, 400f, 950f, 1410f)
        canvas.save()
        canvas.rotate(-4f, 540f, 905f)
        canvas.drawRoundRect(frame, 14f, 14f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#FAFAF7")
            setShadowLayer(70f, 0f, 30f, Color.argb(150, 0, 0, 0))
        })
        val photo = RectF(frame.left + 48f, frame.top + 48f, frame.right - 48f, frame.top + 48f + 724f)
        drawCoverRect(context, canvas, cover, photo, 6f, 0)

        val textWidth = photo.width()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#111111")
            textSize = 58f
            textSkewX = -0.12f
            typeface = geist(context, R.font.geist_w700)
        }
        canvas.drawText(ellipsizeText(title, titlePaint, textWidth), photo.left, photo.bottom + 92f, titlePaint)
        if (artist.isNotBlank()) {
            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#555555")
                textSize = 40f
                textSkewX = -0.12f
                typeface = geist(context, R.font.geist_w400)
            }
            canvas.drawText(ellipsizeText(artist, artistPaint, textWidth), photo.left, photo.bottom + 154f, artistPaint)
        }
        // Strip of tape holding the photo
        canvas.save()
        canvas.rotate(3f, 540f, frame.top)
        canvas.drawRect(440f, frame.top - 34f, 640f, frame.top + 34f, Paint().apply {
            color = Color.argb(120, 255, 255, 255)
        })
        canvas.restore()
        canvas.restore()

        drawFooter(context, canvas, host, FOOTER_Y)
    }

    /** Giant stacked title, a small round cover and mono metadata. */
    private fun drawMinimal(
        context: Context, canvas: Canvas, cover: Bitmap?, palette: Palette?,
        title: String, artist: String, host: String, label: String
    ) {
        canvas.drawColor(Color.parseColor("#0A0A0A"))
        drawCoverGlowBackdrop(canvas, palette)
        drawHeader(context, canvas, label, Color.WHITE, false)
        val accent = accentOf(palette, Color.WHITE)

        val width = (IMAGE_WIDTH - MARGIN * 2).toInt()
        val bold = geist(context, R.font.geist_w700)
        val layout = fitLayout(
            title, listOf(210f, 180f, 150f, 124f, 100f, 84f), bold, Color.WHITE, width, 3, -0.055f, 0.9f
        )
        val top = maxOf(420f, 1250f - layout.height)
        canvas.drawRoundRect(RectF(MARGIN, top - 60f, MARGIN + 120f, top - 50f), 5f, 5f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent
        })
        canvas.save()
        canvas.translate(MARGIN, top)
        layout.draw(canvas)
        canvas.restore()

        val thumb = 150f
        val thumbTop = 1370f
        drawCoverCircle(context, canvas, cover, MARGIN + thumb / 2f, thumbTop + thumb / 2f, thumb / 2f)
        val textLeft = MARGIN + thumb + 36f
        val textWidth = IMAGE_WIDTH - MARGIN - textLeft
        if (artist.isNotBlank()) {
            val artistPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                textSize = 50f
                typeface = bold
            }
            canvas.drawText(ellipsizeText(artist, artistPaint, textWidth), textLeft, thumbTop + thumb / 2f + 17f, artistPaint)
        }
        drawFooter(context, canvas, host, FOOTER_Y)
    }

    /** A till receipt on a dark frame: mono lines, dashed rules, a black and white cover, a barcode. */
    private fun drawReceipt(
        context: Context, canvas: Canvas, cover: Bitmap?, palette: Palette?,
        title: String, artist: String, host: String, label: String
    ) {
        canvas.drawColor(Color.parseColor("#0E0E0E"))
        drawCoverGlowBackdrop(canvas, palette)
        drawHeader(context, canvas, label, Color.WHITE, false)

        val paperLeft = 130f
        val paperRight = 950f
        val paperTop = 360f
        val ink = Color.parseColor("#111111")
        val gray = Color.parseColor("#5C5C58")
        val mono = geist(context, R.font.geist_mono_w400)
        drawReceiptPaper(canvas, paperLeft, paperTop, paperRight, 1590f)

        val textLeft = paperLeft + 60f
        val textRight = paperRight - 60f
        val width = (textRight - textLeft).toInt()
        val centre = (paperLeft + paperRight) / 2f

        val head = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 46f
            letterSpacing = 0.12f
            typeface = mono
            isFakeBoldText = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("YAMMBO MUSIC", centre, paperTop + 92f, head)
        val sub = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray
            textSize = 26f
            letterSpacing = 0.14f
            typeface = mono
            textAlign = Paint.Align.CENTER
        }
        drawDashed(canvas, textLeft, textRight, paperTop + 176f, ink)

        val coverSize = 280f
        val coverRect = RectF(centre - coverSize / 2f, paperTop + 212f, centre + coverSize / 2f, paperTop + 212f + coverSize)
        drawCoverRect(context, canvas, cover, coverRect, 0f, 0, grayscale = true)
        drawDashed(canvas, textLeft, textRight, coverRect.bottom + 38f, ink)

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = gray
            textSize = 24f
            letterSpacing = 0.14f
            typeface = mono
        }
        var y = coverRect.bottom + 38f + 62f
        fun field(name: String, value: String, maxLines: Int) {
            canvas.drawText(name.uppercase(), textLeft, y, labelPaint)
            val layout = monoLayout(value, 40f, mono, ink, width, maxLines, true)
            canvas.save()
            canvas.translate(textLeft, y + 18f)
            layout.draw(canvas)
            canvas.restore()
            y += 18f + layout.height + 54f
        }
        field(context.getString(R.string.share_receipt_track), title, 2)
        if (artist.isNotBlank()) field(context.getString(R.string.share_receipt_artist), artist, 1)
        field(
            context.getString(R.string.share_receipt_date),
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date()), 1
        )

        drawDashed(canvas, textLeft, textRight, 1385f, ink)
        drawBarcode(canvas, textLeft, textRight, 1410f, 1475f, (title + artist).hashCode().toLong(), ink)
        if (host.isNotBlank()) {
            val hostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ink
                textSize = 28f
                letterSpacing = 0.08f
                typeface = mono
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(host, centre, 1518f, hostPaint)
        }
        canvas.drawText(
            context.getString(R.string.share_receipt_thanks).uppercase(), centre, 1555f,
            Paint(sub).apply { textSize = 22f }
        )
    }

    /** Off-white receipt paper with 12 px zigzag (torn) edges top and bottom and a soft shadow. */
    internal fun drawReceiptPaper(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val depth = 12f
        val steps = ((right - left) / 24f).toInt().coerceAtLeast(2)
        val stepW = (right - left) / steps
        val path = Path().apply {
            moveTo(left, top + depth)
            for (i in 1..steps) lineTo(left + i * stepW, if (i % 2 == 1) top else top + depth)
            lineTo(right, bottom - depth)
            for (i in 1..steps) lineTo(right - i * stepW, if (i % 2 == 1) bottom else bottom - depth)
            close()
        }
        canvas.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#F7F7F3")
            setShadowLayer(50f, 0f, 20f, Color.argb(120, 0, 0, 0))
        })
    }

    internal fun drawDashed(canvas: Canvas, left: Float, right: Float, y: Float, color: Int) {
        canvas.drawLine(left, y, right, y, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = 3f
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        })
    }

    /** Barcode-like stripes; the same [seed] always draws the same code. */
    internal fun drawBarcode(canvas: Canvas, left: Float, right: Float, top: Float, bottom: Float, seed: Long, color: Int) {
        val rnd = Random(seed)
        val paint = Paint().apply { this.color = color }
        var x = left
        while (x < right) {
            val w = 3f + rnd.nextInt(3) * 3f
            if (x + w > right) break
            canvas.drawRect(x, top, x + w, bottom, paint)
            x += w + 3f + rnd.nextInt(2) * 3f
        }
    }

    internal fun monoLayout(
        text: String, size: Float, face: Typeface, color: Int, width: Int, maxLines: Int, bold: Boolean
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = face
            isFakeBoldText = bold
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    /** The biggest of [sizes] at which [text] fits in [maxLines]; the smallest, ellipsised, if none does. */
    internal fun fitLayout(
        text: String, sizes: List<Float>, face: Typeface, color: Int, width: Int,
        maxLines: Int, tracking: Float, spacingMult: Float
    ): StaticLayout {
        for (s in sizes) {
            val l = bigLayout(text, s, face, color, width, maxLines + 1, tracking, spacingMult)
            if (l.lineCount <= maxLines) return l
        }
        return bigLayout(text, sizes.last(), face, color, width, maxLines, tracking, spacingMult)
    }

    private fun bigLayout(
        text: String, size: Float, face: Typeface, color: Int, width: Int,
        maxLines: Int, tracking: Float, spacingMult: Float
    ): StaticLayout {
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = size
            typeface = face
            letterSpacing = tracking
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, spacingMult)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    /** The strongest colour of the cover, or [fallback] when there is no artwork. */
    internal fun accentOf(palette: Palette?, fallback: Int): Int =
        palette?.let { it.getVibrantColor(it.getMutedColor(it.getDominantColor(fallback))) } ?: fallback

    /**
     * Scales the artwork to cover the whole frame, blurs it by downscaling to a few
     * dozen pixels and letting the bilinear filter smear it back up (no RenderScript,
     * works on every supported API).
     */
    internal fun drawBlurredBackdrop(canvas: Canvas, cover: Bitmap?, palette: Palette?) {
        if (cover == null) {
            drawBackground(canvas, palette)
            return
        }

        // Crop the artwork to the frame's aspect ratio BEFORE scaling. Skipping this
        // stretches a square cover into 9:16 and visibly distorts it.
        val targetRatio = IMAGE_WIDTH.toFloat() / IMAGE_HEIGHT
        val cropW = minOf(cover.width.toFloat(), cover.height * targetRatio)
        val cropH = cropW / targetRatio
        val cropX = ((cover.width - cropW) / 2f).toInt().coerceAtLeast(0)
        val cropY = ((cover.height - cropH) / 2f).toInt().coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(
            cover,
            cropX,
            cropY,
            cropW.toInt().coerceAtMost(cover.width - cropX),
            cropH.toInt().coerceAtMost(cover.height - cropY)
        )

        // Going up in two steps smooths out the blocky diamonds a single 30x bilinear
        // stretch leaves behind.
        val tiny = Bitmap.createScaledBitmap(cropped, 36, 64, true)
        val mid = Bitmap.createScaledBitmap(tiny, 180, 320, true)
        val dest = RectF(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat())
        val smoothPaint = Paint().apply {
            isFilterBitmap = true
            isAntiAlias = true
            // A touch more saturation: the blur and the veil wash the colours out.
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.35f) })
        }

        canvas.drawBitmap(mid, null, dest, smoothPaint)
        mid.recycle()
        tiny.recycle()
        if (cropped != cover) cropped.recycle()
    }

    /** Darkening veil: at least 35% black everywhere, heavy at the bottom where the footer sits. */
    internal fun drawVeil(canvas: Canvas) {
        canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), Paint().apply {
            shader = LinearGradient(
                0f, 0f, 0f, IMAGE_HEIGHT.toFloat(),
                intArrayOf(
                    Color.parseColor("#66000000"),
                    Color.parseColor("#59000000"),
                    Color.parseColor("#D9000000")
                ),
                floatArrayOf(0f, 0.4f, 1f),
                Shader.TileMode.CLAMP
            )
        })
    }

    /** Black frame with a soft pool of the cover's own colour at the top-left. */
    private fun drawCoverGlowBackdrop(canvas: Canvas, palette: Palette?) {
        val raw = palette?.let {
            it.getVibrantColor(it.getMutedColor(it.getDominantColor(Color.TRANSPARENT)))
        } ?: Color.TRANSPARENT
        if (raw == Color.TRANSPARENT) return
        // Tamed to a dark, desaturated tint so white type on top always keeps its contrast.
        val base = darkBackground(raw)
        canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), Paint().apply {
            shader = RadialGradient(
                IMAGE_WIDTH * 0.25f, IMAGE_HEIGHT * 0.2f, IMAGE_WIDTH * 1.2f,
                Color.argb(170, Color.red(base), Color.green(base), Color.blue(base)),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP
            )
        })
    }

    /** Dark grey with fine 45 degree stripes. */
    private fun drawGrain(canvas: Canvas) {
        canvas.drawColor(Color.parseColor("#1B1B1B"))
        val stripe = Paint().apply {
            color = Color.parseColor("#262626")
            strokeWidth = 9f
        }
        canvas.save()
        canvas.rotate(45f, IMAGE_WIDTH / 2f, IMAGE_HEIGHT / 2f)
        val span = IMAGE_HEIGHT + IMAGE_WIDTH
        var y = -span / 2f
        while (y < span.toFloat()) {
            canvas.drawLine(-span.toFloat(), y, span * 2f, y, stripe)
            y += 18f
        }
        canvas.restore()
    }

    private fun drawBackground(canvas: Canvas, palette: Palette?) {
        val paint = Paint()

        // Extract vibrant colors from album art — use lighter swatches first
        val vibrantColor = palette?.getVibrantColor(
            palette.getMutedColor(Color.parseColor("#2A2A2A"))
        ) ?: Color.parseColor("#2A2A2A")

        val dominantColor = palette?.getDominantColor(
            palette.getDarkVibrantColor(Color.parseColor("#161616"))
        ) ?: Color.parseColor("#161616")

        // Slightly darken for text contrast, but keep colors visible
        val topColor = darkenColor(vibrantColor, 0.7f)
        val midColor = darkenColor(dominantColor, 0.55f)
        val bottomColor = darkenColor(dominantColor, 0.3f)

        paint.shader = LinearGradient(
            0f, 0f, 0f, IMAGE_HEIGHT.toFloat(),
            intArrayOf(topColor, midColor, bottomColor),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat(), paint)
    }

    private fun darkenColor(color: Int, factor: Float): Int {
        val r = (Color.red(color) * factor).toInt().coerceIn(0, 255)
        val g = (Color.green(color) * factor).toInt().coerceIn(0, 255)
        val b = (Color.blue(color) * factor).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    // Last cover downloaded, kept small: the share sheet renders the card again for every
    // style the user tries, and downloading the artwork each time made switching slow.
    private var cachedCoverUrl: String? = null
    private var cachedCover: Bitmap? = null
    private const val CACHED_COVER_MAX = 1080

    /** Returns a bitmap the caller owns (and may recycle). */
    internal fun loadCoverArt(thumbnailUrl: String?): Bitmap? {
        if (thumbnailUrl.isNullOrEmpty() || thumbnailUrl == "null") return null

        synchronized(this) {
            val hit = cachedCover
            if (hit != null && !hit.isRecycled && cachedCoverUrl == thumbnailUrl)
                return hit.copy(Bitmap.Config.ARGB_8888, false)
        }

        val loaded = downloadCoverArt(thumbnailUrl) ?: return null
        val small = if (maxOf(loaded.width, loaded.height) > CACHED_COVER_MAX) {
            val k = CACHED_COVER_MAX.toFloat() / maxOf(loaded.width, loaded.height)
            Bitmap.createScaledBitmap(
                loaded, (loaded.width * k).toInt().coerceAtLeast(1), (loaded.height * k).toInt().coerceAtLeast(1), true
            )
        } else loaded.copy(Bitmap.Config.ARGB_8888, false)
        synchronized(this) {
            cachedCover = small
            cachedCoverUrl = thumbnailUrl
        }
        return loaded
    }

    private fun downloadCoverArt(thumbnailUrl: String): Bitmap? {
        // Try a chain of progressively-lower resolution URLs. Stops at the first one
        // that actually downloads — avoids the upscale-blur that happened when the
        // single-attempt maxresdefault came back 404 and we fell straight to hqdefault.
        val candidates = buildList {
            when {
                thumbnailUrl.contains("lh3.googleusercontent.com") ||
                thumbnailUrl.contains("yt3.googleusercontent.com") ||
                thumbnailUrl.contains("yt3.ggpht.com") -> {
                    val base = thumbnailUrl.substringBefore("=", thumbnailUrl)
                    add("$base=w2000-h2000-l90-rj")
                    add("$base=w1200-h1200-l90-rj")
                    add("$base=w800-h800-l90-rj")
                    if (thumbnailUrl != base) add(thumbnailUrl) // original sized variant
                }
                thumbnailUrl.contains("i.ytimg.com") -> {
                    // Try YT video thumbnail sizes from highest to lowest.
                    val variants = listOf("maxresdefault", "sddefault", "hqdefault", "mqdefault")
                    val normalised = thumbnailUrl
                        .replace("hqdefault", "PLACEHOLDER")
                        .replace("mqdefault", "PLACEHOLDER")
                        .replace("sddefault", "PLACEHOLDER")
                        .replace("maxresdefault", "PLACEHOLDER")
                        .replace("default", "PLACEHOLDER")
                    if (normalised.contains("PLACEHOLDER")) {
                        variants.forEach { v -> add(normalised.replace("PLACEHOLDER", v)) }
                    } else {
                        add(thumbnailUrl)
                    }
                }
                else -> add(thumbnailUrl)
            }
        }

        for (candidate in candidates) {
            val bmp = fetchBitmap(candidate) ?: continue
            // Reject obvious upscale candidates — anything smaller than the target
            // would just get blown up by createScaledBitmap. Try the next URL instead.
            if (minOf(bmp.width, bmp.height) >= COVER_SIZE) return bmp
            // Keep as last-resort: if no candidate beats COVER_SIZE we'll return the
            // largest one we've seen so far.
            if (candidate == candidates.last()) return bmp
            bmp.recycle()
        }
        return null
    }

    private fun fetchBitmap(url: String): Bitmap? = try {
        val connection = URL(url).openConnection().apply {
            connectTimeout = 10000
            readTimeout = 10000
        }
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeStream(connection.getInputStream(), null, options)
    } catch (_: Exception) {
        null
    }

    /**
     * Draws the cover centre-cropped into [rect] with rounded corners and, when [shadowAlpha] is
     * above zero, a deep shadow. Without artwork it draws a dark tile with the Yammbo mark, never
     * a coloured placeholder.
     */
    internal fun drawCoverRect(
        context: Context,
        canvas: Canvas,
        cover: Bitmap?,
        rect: RectF,
        radius: Float,
        shadowAlpha: Int,
        grayscale: Boolean = false
    ) {
        if (shadowAlpha > 0) {
            canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#40000000")
                setShadowLayer(80f, 0f, 30f, Color.argb(shadowAlpha, 0, 0, 0))
            })
        }
        canvas.save()
        canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
        if (cover != null) {
            val dstRatio = rect.width() / rect.height()
            val srcRatio = cover.width.toFloat() / cover.height
            val src = if (srcRatio > dstRatio) {
                val w = cover.height * dstRatio
                val x = (cover.width - w) / 2f
                Rect(x.toInt(), 0, (x + w).toInt().coerceAtMost(cover.width), cover.height)
            } else {
                val h = cover.width / dstRatio
                val y = (cover.height - h) / 2f
                Rect(0, y.toInt(), cover.width, (y + h).toInt().coerceAtMost(cover.height))
            }
            canvas.drawBitmap(cover, src, rect, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                if (grayscale) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
            })
        } else {
            canvas.drawRect(rect, Paint().apply { color = Color.parseColor("#1A1A1C") })
            drawIcon(
                context, canvas, rect.centerX(), rect.centerY(),
                minOf(rect.width(), rect.height()) * 0.28f, Color.argb(90, 255, 255, 255)
            )
        }
        canvas.restore()
    }

    /** The cover cropped to a circle centred on [cx], [cy]. */
    internal fun drawCoverCircle(context: Context, canvas: Canvas, cover: Bitmap?, cx: Float, cy: Float, radius: Float) {
        canvas.save()
        canvas.clipPath(Path().apply { addCircle(cx, cy, radius, Path.Direction.CW) })
        drawCoverRect(context, canvas, cover, RectF(cx - radius, cy - radius, cx + radius, cy + radius), 0f, 0)
        canvas.restore()
    }

    /**
     * Title (up to two lines, ellipsised) and artist (one line). A long title steps down in
     * size instead of being cut to a few words.
     */
    private fun drawTextBlock(
        canvas: Canvas,
        title: String,
        artist: String,
        top: Float,
        ink: Int,
        inkSoft: Int,
        width: Int,
        context: Context
    ) {
        val bold = geist(context, R.font.geist_w700)
        val regular = geist(context, R.font.geist_w400)
        val titleLayout = textLayout(title, 96f, bold, ink, width, 2, -0.04f).let { full ->
            if (full.lineCount > 1) textLayout(title, 80f, bold, ink, width, 2, -0.04f) else full
        }
        canvas.save()
        canvas.translate(MARGIN, top)
        titleLayout.draw(canvas)
        canvas.restore()

        if (artist.isNotEmpty()) {
            val artistLayout = textLayout(artist, 48f, regular, inkSoft, width, 1, 0f)
            canvas.save()
            canvas.translate(MARGIN, top + titleLayout.height + 18f)
            artistLayout.draw(canvas)
            canvas.restore()
        }
    }

    /** Bottom box: the Yammbo mark in a tile, "Listen on Yammbo Music" and the host. */
    private fun drawLinkBox(
        context: Context,
        canvas: Canvas,
        host: String,
        ink: Int,
        inkSoft: Int,
        onLight: Boolean,
        left: Float,
        right: Float
    ) {
        val top = LINK_BOX_BOTTOM - LINK_BOX_HEIGHT
        val box = RectF(left, top, right, LINK_BOX_BOTTOM)

        canvas.drawRoundRect(box, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (onLight) Color.WHITE else Color.argb(102, 0, 0, 0)
        })
        canvas.drawRoundRect(box, 48f, 48f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = if (onLight) Color.argb(26, 0, 0, 0) else Color.argb(46, 255, 255, 255)
        })

        val tile = RectF(left + 20f, top + 20f, left + 116f, top + 116f)
        canvas.drawRoundRect(tile, 26f, 26f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (onLight) INK_DARK else Color.WHITE
        })
        drawIcon(
            context, canvas, tile.centerX(), tile.centerY(), 60f,
            if (onLight) Color.WHITE else INK_DARK
        )

        val textLeft = tile.right + 30f
        val textWidth = right - 30f - textLeft
        val headPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 38f
            typeface = geist(context, R.font.geist_w700)
        }
        val head = ellipsizeText(context.getString(R.string.share_story_listen), headPaint, textWidth)
        canvas.drawText(head, textLeft, top + LINK_BOX_HEIGHT / 2f - 6f, headPaint)

        val urlPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = inkSoft
            textSize = 26f
            typeface = geist(context, R.font.geist_mono_w400)
        }
        val shown = ellipsizeText(host, urlPaint, textWidth)
        canvas.drawText(shown, textLeft, top + LINK_BOX_HEIGHT / 2f + 38f, urlPaint)
    }

    private fun textLayout(
        text: String,
        size: Float,
        face: Typeface,
        color: Int,
        width: Int,
        maxLines: Int,
        tracking: Float
    ): StaticLayout {
        val paint = TextPaint().apply {
            this.color = color
            textSize = size
            typeface = face
            letterSpacing = tracking
            isAntiAlias = true
        }
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
            .setIncludePad(false)
            .build()
    }

    /** Geist from the app's own fonts; the platform bold/regular if the resource is missing. */
    internal fun geist(context: Context, @FontRes resId: Int): Typeface =
        runCatching { ResourcesCompat.getFont(context, resId) }.getOrNull() ?: Typeface.DEFAULT

    internal fun ellipsizeText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var t = text
        while (t.isNotEmpty() && paint.measureText("$t…") > maxWidth) t = t.dropLast(1)
        return "$t…"
    }

    /** The Yammbo wordmark, [height] px tall, left edge at [left]. White on dark, black on paper. */
    internal fun drawLogo(context: Context, canvas: Canvas, left: Float, centerY: Float, height: Float, onLight: Boolean) {
        runCatching {
            // The artwork is thousands of pixels wide; decode it a quarter of the size.
            val logo = BitmapFactory.decodeResource(
                context.resources,
                if (onLight) R.drawable.yambo_logo_dark else R.drawable.yambo_logo_light,
                BitmapFactory.Options().apply { inSampleSize = 4 }
            ) ?: return
            val width = height * logo.width / logo.height
            canvas.drawBitmap(
                logo, null,
                RectF(left, centerY - height / 2f, left + width, centerY + height / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            )
            logo.recycle()
        }
    }

    /** The round-cornered Yammbo mark, tinted to [color], centred on [cx], [cy]. */
    internal fun drawIcon(context: Context, canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        runCatching {
            val icon = BitmapFactory.decodeResource(
                context.resources, R.drawable.yambo_icon,
                BitmapFactory.Options().apply { inSampleSize = 2 }
            ) ?: return
            canvas.drawBitmap(
                icon, null,
                RectF(cx - size / 2f, cy - size / 2f, cx + size / 2f, cy + size / 2f),
                Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                    colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
                }
            )
            icon.recycle()
        }
    }

    /** Mono uppercase pill, right edge at [right], vertically centred on [centerY]. */
    internal fun drawPill(
        context: Context,
        canvas: Canvas,
        label: String,
        right: Float,
        centerY: Float,
        ink: Int,
        onLight: Boolean
    ) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textSize = 27f
            letterSpacing = 0.14f
            typeface = geist(context, R.font.geist_mono_w400)
        }
        val caption = label.uppercase()
        val textWidth = text.measureText(caption)
        val padX = 27f
        val height = 66f
        val pill = RectF(right - textWidth - padX * 2, centerY - height / 2f, right, centerY + height / 2f)
        canvas.drawRoundRect(pill, height / 2f, height / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (onLight) Color.argb(15, 0, 0, 0) else Color.argb(89, 0, 0, 0)
        })
        canvas.drawRoundRect(pill, height / 2f, height / 2f, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = if (onLight) Color.argb(60, 0, 0, 0) else Color.argb(64, 255, 255, 255)
        })
        canvas.drawText(caption, pill.left + padX, centerY + 9f, text)
    }

    internal fun centerCropBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val size = minOf(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2
        val cropped = Bitmap.createBitmap(source, x, y, size, size)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped != source) cropped.recycle()
        return scaled
    }

    internal fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap): Uri? {
        val cacheDir = File(context.cacheDir, "share_images")
        cacheDir.mkdirs()
        val file = File(cacheDir, "yammbo_share_${System.currentTimeMillis()}.png")
        file.outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()

        // Clean old share images (keep only last 5)
        cacheDir.listFiles()
            ?.sortedByDescending { it.lastModified() }
            ?.drop(5)
            ?.forEach { it.delete() }

        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
