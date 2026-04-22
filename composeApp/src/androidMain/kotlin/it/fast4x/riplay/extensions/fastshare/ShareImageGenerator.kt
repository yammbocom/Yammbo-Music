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
    private const val COVER_SIZE = 680
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

            // 3. Draw cover art
            val coverTop = drawCoverArt(canvas, coverBitmap)

            // 4. Draw song title
            val titleBottom = drawTitle(canvas, title, (coverTop + COVER_SIZE + 60).toFloat())

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
        return try {
            // Request high resolution cover art
            val highResSize = 1200
            val url = when {
                thumbnailUrl.contains("lh3.googleusercontent.com") -> {
                    // YouTube Music thumbnails — request max quality
                    val base = thumbnailUrl.split("=").firstOrNull() ?: thumbnailUrl
                    "$base=w$highResSize-h$highResSize-l90-rj"
                }
                thumbnailUrl.contains("i.ytimg.com") -> {
                    // YouTube video thumbnails — use maxresdefault
                    thumbnailUrl.replace("hqdefault", "maxresdefault")
                        .replace("mqdefault", "maxresdefault")
                        .replace("sddefault", "maxresdefault")
                }
                else -> thumbnailUrl
            }
            val connection = URL(url).openConnection().apply {
                connectTimeout = 10000
                readTimeout = 10000
            }
            BitmapFactory.decodeStream(connection.getInputStream())
        } catch (e: Exception) {
            // Fallback to original URL if high-res fails
            try {
                val connection = URL(thumbnailUrl).openConnection().apply {
                    connectTimeout = 10000
                    readTimeout = 10000
                }
                BitmapFactory.decodeStream(connection.getInputStream())
            } catch (e2: Exception) {
                null
            }
        }
    }

    private fun drawCoverArt(canvas: Canvas, coverBitmap: Bitmap?): Int {
        val left = (IMAGE_WIDTH - COVER_SIZE) / 2f
        // Center cover in the upper portion, leaving room for title + artist + branding below
        val top = (IMAGE_HEIGHT - COVER_SIZE - 300) / 2.5f

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

        return top.toInt()
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
