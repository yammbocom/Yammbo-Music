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
    private const val COVER_SIZE = 680          // min acceptable source resolution
    // Cover is drawn at its natural aspect ratio (no crop), fitted inside this box.
    private const val COVER_MAX_W = 820
    private const val COVER_MAX_H = 960
    private const val PLACEHOLDER_SIZE = 680
    private const val COVER_RADIUS = 32f
    private const val PADDING = 80

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

            // 1. Load cover art first to extract colors
            val coverBitmap = loadCoverArt(thumbnailUrl)
            val palette = coverBitmap?.let {
                Palette.from(it).maximumColorCount(16).generate()
            }

            // 2. Draw background based on cover art colors
            drawBackground(canvas, palette)

            // 3. Draw cover art (full image, no crop) — returns its bottom Y
            val coverBottom = drawCoverArt(canvas, coverBitmap)

            // 4. Draw song title
            val titleBottom = drawTitle(canvas, title, (coverBottom + 70).toFloat())

            // 5. Draw artist name
            drawArtist(canvas, artist, titleBottom + 16f)

            // 6. Draw Yammbo Music branding at bottom
            drawBranding(context, canvas)

            // Save to cache and return URI
            saveBitmapAndGetUri(context, bitmap)
        } catch (e: Exception) {
            null
        }
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

    private fun drawCoverArt(canvas: Canvas, coverBitmap: Bitmap?): Int {
        if (coverBitmap != null) {
            // Fit the WHOLE image (preserve aspect ratio, no crop) inside the cover box.
            val scaled = fitBitmap(coverBitmap, COVER_MAX_W, COVER_MAX_H)
            val drawnW = scaled.width
            val drawnH = scaled.height
            val left = (IMAGE_WIDTH - drawnW) / 2f
            // Vertically center the cover + title/artist block in the area above the branding.
            val top = ((IMAGE_HEIGHT - 300) - drawnH - 180) / 2f
            val rect = RectF(left, top, left + drawnW, top + drawnH)

            // Draw shadow behind cover
            val shadowPaint = Paint().apply {
                color = Color.parseColor("#40000000")
                setShadowLayer(40f, 0f, 16f, Color.parseColor("#80000000"))
            }
            canvas.drawRoundRect(rect, COVER_RADIUS, COVER_RADIUS, shadowPaint)

            // Clip to rounded rectangle and draw the full cover
            canvas.save()
            val path = android.graphics.Path().apply {
                addRoundRect(rect, COVER_RADIUS, COVER_RADIUS, android.graphics.Path.Direction.CW)
            }
            canvas.clipPath(path)
            canvas.drawBitmap(scaled, left, top, null)
            canvas.restore()

            scaled.recycle()
            return (top + drawnH).toInt()
        } else {
            // Placeholder: dark rounded square with music note
            val size = PLACEHOLDER_SIZE
            val left = (IMAGE_WIDTH - size) / 2f
            val top = ((IMAGE_HEIGHT - 300) - size - 180) / 2f
            val rect = RectF(left, top, left + size, top + size)

            val placeholderPaint = Paint().apply {
                color = Color.parseColor("#2a2a3e")
                isAntiAlias = true
            }
            canvas.drawRoundRect(rect, COVER_RADIUS, COVER_RADIUS, placeholderPaint)
            val notePaint = Paint().apply {
                color = Color.parseColor("#555566")
                textSize = 200f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT
            }
            canvas.drawText("\u266B", IMAGE_WIDTH / 2f, top + size / 2f + 70f, notePaint)

            return (top + size).toInt()
        }
    }

    private fun drawTitle(canvas: Canvas, title: String, y: Float): Float {
        val paint = Paint().apply {
            color = Color.WHITE
            textSize = 52f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val maxWidth = IMAGE_WIDTH - PADDING * 2
        val lines = wrapText(title, paint, maxWidth.toFloat())
        var currentY = y

        for (line in lines.take(2)) {
            canvas.drawText(line, IMAGE_WIDTH / 2f, currentY, paint)
            currentY += 64f
        }

        return currentY
    }

    private fun drawArtist(canvas: Canvas, artist: String, y: Float): Float {
        if (artist.isEmpty()) return y

        val paint = Paint().apply {
            color = Color.parseColor("#B0B0B0")
            textSize = 38f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }

        val maxWidth = IMAGE_WIDTH - PADDING * 2
        val lines = wrapText(artist, paint, maxWidth.toFloat())
        var currentY = y

        for (line in lines.take(1)) {
            canvas.drawText(line, IMAGE_WIDTH / 2f, currentY, paint)
            currentY += 48f
        }

        return currentY
    }

    private fun drawBranding(context: Context, canvas: Canvas) {
        val bottomY = IMAGE_HEIGHT - 160f

        // Subtle separator line above branding
        val linePaint = Paint().apply {
            color = Color.parseColor("#30FFFFFF")
            strokeWidth = 1f
        }
        canvas.drawLine(
            PADDING.toFloat(), bottomY - 80f,
            (IMAGE_WIDTH - PADDING).toFloat(), bottomY - 80f,
            linePaint
        )

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

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = ""

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = testLine
            } else {
                if (currentLine.isNotEmpty()) lines.add(currentLine)
                currentLine = word
            }
        }
        if (currentLine.isNotEmpty()) {
            if (lines.size >= 1 && paint.measureText(currentLine) > maxWidth) {
                var truncated = currentLine
                while (paint.measureText("$truncated...") > maxWidth && truncated.isNotEmpty()) {
                    truncated = truncated.dropLast(1)
                }
                lines.add("$truncated...")
            } else {
                lines.add(currentLine)
            }
        }

        return lines
    }

    // Scale the whole bitmap to fit inside (maxW x maxH) preserving aspect ratio — no crop.
    private fun fitBitmap(source: Bitmap, maxW: Int, maxH: Int): Bitmap {
        val scale = minOf(maxW.toFloat() / source.width, maxH.toFloat() / source.height)
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, w, h, true)
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
