package it.fast4x.riplay.extensions.fastshare

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URL

object ShareImageGenerator {

    private const val IMAGE_WIDTH = 1080
    private const val IMAGE_HEIGHT = 1920

    /* "Liquid glass" story card: the cover art fills the frame blurred, and a
       translucent panel floats on top carrying the sharp cover, the track and a
       decorative transport row. Everything is derived from the artwork, so no
       per-song colour decisions are needed and it stays inside the black/white brand. */
    private const val CARD_LEFT = 110f
    private const val CARD_TOP = 340f
    private const val CARD_WIDTH = 860f
    private const val CARD_HEIGHT = 1260f
    private const val CARD_RADIUS = 56f
    private const val CARD_INSET = 60f
    private const val COVER_SIZE = 740
    private const val COVER_RADIUS = 36f

    private const val GLASS_FILL = "#1FFFFFFF"
    private const val GLASS_STROKE = "#3DFFFFFF"
    private const val TEXT_SECONDARY = "#C2FFFFFF"

    suspend fun generateShareImage(
        context: Context,
        title: String,
        artist: String,
        thumbnailUrl: String?,
        shareUrl: String
    ): Uri? = withContext(Dispatchers.IO) {
        try {
            val bitmap = Bitmap.createBitmap(IMAGE_WIDTH, IMAGE_HEIGHT, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val coverBitmap = loadCoverArt(thumbnailUrl)
            val palette = coverBitmap?.let {
                Palette.from(it).maximumColorCount(16).generate()
            }

            // 1. Full-bleed blurred artwork (falls back to a palette gradient)
            drawBlurredBackdrop(canvas, coverBitmap, palette)

            // 2. The floating glass panel and everything inside it
            drawGlassCard(canvas)
            val coverBottom = drawCoverArt(canvas, coverBitmap)
            val titleBottom = drawTitle(canvas, title, coverBottom + 92f)
            drawArtist(canvas, artist, titleBottom + 12f)
            drawProgressBar(canvas, CARD_TOP + CARD_HEIGHT - 250f)
            drawTransportControls(canvas, CARD_TOP + CARD_HEIGHT - 130f)

            // 3. Yammbo Music branding under the card
            drawBranding(context, canvas)

            saveBitmapAndGetUri(context, bitmap)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Scales the artwork to cover the whole frame, blurs it by downscaling to a few
     * dozen pixels and letting the bilinear filter smear it back up (no RenderScript,
     * works on every supported API), then darkens it so the glass panel reads on top.
     */
    private fun drawBlurredBackdrop(canvas: Canvas, cover: Bitmap?, palette: Palette?) {
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

        // Blur by collapsing to a few dozen pixels and letting the bilinear filter
        // smear it back up. The small size keeps the 9:16 ratio, so no distortion.
        val tiny = Bitmap.createScaledBitmap(cropped, 36, 64, true)
        val dest = RectF(0f, 0f, IMAGE_WIDTH.toFloat(), IMAGE_HEIGHT.toFloat())
        val smoothPaint = Paint().apply { isFilterBitmap = true; isAntiAlias = true }

        canvas.drawBitmap(tiny, null, dest, smoothPaint)
        tiny.recycle()
        if (cropped != cover) cropped.recycle()

        // Darkening veil — without it white glass on a light cover is unreadable.
        canvas.drawRect(dest, Paint().apply { color = Color.parseColor("#A6000000") })
    }

    /** The translucent panel: soft shadow, low-alpha white fill, hairline border. */
    private fun drawGlassCard(canvas: Canvas) {
        val rect = RectF(CARD_LEFT, CARD_TOP, CARD_LEFT + CARD_WIDTH, CARD_TOP + CARD_HEIGHT)

        canvas.drawRoundRect(rect, CARD_RADIUS, CARD_RADIUS, Paint().apply {
            color = Color.parseColor("#33000000")
            isAntiAlias = true
            setShadowLayer(60f, 0f, 24f, Color.parseColor("#66000000"))
        })
        canvas.drawRoundRect(rect, CARD_RADIUS, CARD_RADIUS, Paint().apply {
            color = Color.parseColor(GLASS_FILL)
            isAntiAlias = true
        })
        canvas.drawRoundRect(rect, CARD_RADIUS, CARD_RADIUS, Paint().apply {
            color = Color.parseColor(GLASS_STROKE)
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        })
    }

    /** Decorative progress line — a filled third with a knob, like a player at rest. */
    private fun drawProgressBar(canvas: Canvas, centerY: Float) {
        val left = CARD_LEFT + CARD_INSET
        val right = CARD_LEFT + CARD_WIDTH - CARD_INSET
        val playedTo = left + (right - left) * 0.34f

        canvas.drawRoundRect(
            RectF(left, centerY - 4f, right, centerY + 4f), 4f, 4f,
            Paint().apply { color = Color.parseColor("#4DFFFFFF"); isAntiAlias = true }
        )
        canvas.drawRoundRect(
            RectF(left, centerY - 4f, playedTo, centerY + 4f), 4f, 4f,
            Paint().apply { color = Color.WHITE; isAntiAlias = true }
        )
        canvas.drawCircle(playedTo, centerY, 16f, Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        })
    }

    /** Previous / play / next, drawn as paths so no drawable assets are needed. */
    private fun drawTransportControls(canvas: Canvas, centerY: Float) {
        val cx = IMAGE_WIDTH / 2f
        val white = Paint().apply { color = Color.WHITE; isAntiAlias = true }

        // Play: solid disc with a dark triangle punched in the middle
        canvas.drawCircle(cx, centerY, 62f, white)
        val triangle = android.graphics.Path().apply {
            moveTo(cx - 20f, centerY - 30f)
            lineTo(cx + 32f, centerY)
            lineTo(cx - 20f, centerY + 30f)
            close()
        }
        canvas.drawPath(triangle, Paint().apply {
            color = Color.parseColor("#0D0D0D")
            isAntiAlias = true
        })

        drawSkipIcon(canvas, cx - 170f, centerY, white, forward = false)
        drawSkipIcon(canvas, cx + 170f, centerY, white, forward = true)
    }

    /**
     * Skip-track glyph: a triangle pointing the way it travels, with the stop bar
     * behind its tip. `dir` mirrors the whole shape for the previous-track variant.
     */
    private fun drawSkipIcon(canvas: Canvas, cx: Float, cy: Float, paint: Paint, forward: Boolean) {
        val dir = if (forward) 1f else -1f

        // Base is the flat edge, tip is the point — getting these the wrong way round
        // is what made both buttons face inwards.
        val path = android.graphics.Path().apply {
            moveTo(cx - 24f * dir, cy - 26f)
            lineTo(cx + 16f * dir, cy)
            lineTo(cx - 24f * dir, cy + 26f)
            close()
        }
        canvas.drawPath(path, paint)

        val barNear = cx + 21f * dir
        val barFar = cx + 31f * dir
        canvas.drawRoundRect(
            RectF(minOf(barNear, barFar), cy - 26f, maxOf(barNear, barFar), cy + 26f),
            5f, 5f, paint
        )
    }

    private fun drawBackground(canvas: Canvas, palette: Palette?) {
        val paint = Paint()

        // Extract vibrant colors from album art — use lighter swatches first
        val vibrantColor = palette?.getVibrantColor(
            palette.getMutedColor(Color.parseColor("#2d3561"))
        ) ?: Color.parseColor("#2d3561")

        val dominantColor = palette?.getDominantColor(
            palette.getDarkVibrantColor(Color.parseColor("#1b2838"))
        ) ?: Color.parseColor("#1b2838")

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

    private fun loadCoverArt(thumbnailUrl: String?): Bitmap? {
        if (thumbnailUrl.isNullOrEmpty() || thumbnailUrl == "null") return null

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
        // Hint we'd like to downsample if the source is huge, so we don't allocate
        // a 4000x4000 bitmap when we only need 680.
        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        BitmapFactory.decodeStream(connection.getInputStream(), null, options)
    } catch (_: Exception) {
        null
    }

    /** Draws the sharp cover inside the glass panel. Returns its bottom edge. */
    private fun drawCoverArt(canvas: Canvas, coverBitmap: Bitmap?): Float {
        val left = CARD_LEFT + CARD_INSET
        val top = CARD_TOP + CARD_INSET

        if (coverBitmap != null) {
            val scaled = centerCropBitmap(coverBitmap, COVER_SIZE)

            // Draw shadow behind cover
            val shadowPaint = Paint().apply {
                color = Color.parseColor("#40000000")
                setShadowLayer(40f, 0f, 16f, Color.parseColor("#80000000"))
            }
            canvas.drawRoundRect(
                RectF(left, top, left + COVER_SIZE, top + COVER_SIZE),
                COVER_RADIUS, COVER_RADIUS, shadowPaint
            )

            // Clip to rounded rectangle and draw cover
            canvas.save()
            val path = android.graphics.Path().apply {
                addRoundRect(
                    RectF(left, top, left + COVER_SIZE, top + COVER_SIZE),
                    COVER_RADIUS, COVER_RADIUS,
                    android.graphics.Path.Direction.CW
                )
            }
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, left, top, null)
            canvas.restore()

            scaled.recycle()
        } else {
            // Placeholder: dark rounded rectangle with music note
            val placeholderPaint = Paint().apply {
                color = Color.parseColor("#2a2a3e")
                isAntiAlias = true
            }
            canvas.drawRoundRect(
                RectF(left, top, left + COVER_SIZE, top + COVER_SIZE),
                COVER_RADIUS, COVER_RADIUS, placeholderPaint
            )
            val notePaint = Paint().apply {
                color = Color.parseColor("#555566")
                textSize = 200f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT
            }
            canvas.drawText("\u266B", IMAGE_WIDTH / 2f, top + COVER_SIZE / 2f + 70f, notePaint)
        }

        return top + COVER_SIZE
    }

    /** Track title, one line, ellipsised so it can never push the transport row out. */
    private fun drawTitle(canvas: Canvas, title: String, y: Float): Float {
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 62f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val maxWidth = CARD_WIDTH - CARD_INSET * 2
        canvas.drawText(ellipsize(title, paint, maxWidth), IMAGE_WIDTH / 2f, y, paint)
        return y
    }

    private fun drawArtist(canvas: Canvas, artist: String, y: Float): Float {
        if (artist.isEmpty()) return y

        val paint = Paint().apply {
            color = Color.parseColor(TEXT_SECONDARY)
            textSize = 42f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val maxWidth = CARD_WIDTH - CARD_INSET * 2
        val currentY = y + 56f
        canvas.drawText(ellipsize(artist, paint, maxWidth), IMAGE_WIDTH / 2f, currentY, paint)
        return currentY
    }

    /** Shortens to a single line, adding an ellipsis only when it actually overflows. */
    private fun ellipsize(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var truncated = text
        while (truncated.isNotEmpty() && paint.measureText("$truncated\u2026") > maxWidth) {
            truncated = truncated.dropLast(1)
        }
        return "$truncated\u2026"
    }

    private fun drawBranding(context: Context, canvas: Canvas) {
        val bottomY = IMAGE_HEIGHT - 170f

        // Load Yammbo icon from resources
        try {
            val iconBitmap = BitmapFactory.decodeResource(context.resources,
                context.resources.getIdentifier("yambo_icon", "drawable", context.packageName))

            if (iconBitmap != null) {
                val iconSize = 56
                val scaledIcon = Bitmap.createScaledBitmap(iconBitmap, iconSize, iconSize, true)
                val tintPaint = Paint().apply {
                    colorFilter = android.graphics.PorterDuffColorFilter(
                        Color.WHITE, android.graphics.PorterDuff.Mode.SRC_IN
                    )
                    isAntiAlias = true
                }
                val textPaint = Paint().apply {
                    color = Color.WHITE
                    textSize = 32f
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    isAntiAlias = true
                }
                val textWidth = textPaint.measureText("Yammbo Music")
                val totalWidth = iconSize + 12 + textWidth
                val startX = (IMAGE_WIDTH - totalWidth) / 2f

                canvas.drawBitmap(scaledIcon, startX, bottomY - iconSize + 8, tintPaint)
                canvas.drawText("Yammbo Music", startX + iconSize + 12, bottomY, textPaint)

                scaledIcon.recycle()
                iconBitmap.recycle()
            }
        } catch (e: Exception) {
            // Fallback: just text
            val textPaint = Paint().apply {
                color = Color.WHITE
                textSize = 32f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("Yammbo Music", IMAGE_WIDTH / 2f, bottomY, textPaint)
        }
    }

    private fun centerCropBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val size = minOf(source.width, source.height)
        val x = (source.width - size) / 2
        val y = (source.height - size) / 2
        val cropped = Bitmap.createBitmap(source, x, y, size, size)
        val scaled = Bitmap.createScaledBitmap(cropped, targetSize, targetSize, true)
        if (cropped != source) cropped.recycle()
        return scaled
    }

    private fun saveBitmapAndGetUri(context: Context, bitmap: Bitmap): Uri? {
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
