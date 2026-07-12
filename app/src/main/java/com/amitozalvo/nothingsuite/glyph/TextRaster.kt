package com.amitozalvo.nothingsuite.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Rasterizes arbitrary unicode text (event/track titles in any script) into
 * a boolean pixel grid for marquee display on the matrix. The tiny DotFont
 * only covers A–Z/0–9; this handles everything else via the system font.
 */
object TextRaster {

    fun rasterize(text: String, height: Int = 7): Array<BooleanArray> {
        if (text.isBlank()) return emptyArray()

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = height * 1.4f
            color = Color.WHITE
        }
        val metrics = paint.fontMetrics
        val textHeight = metrics.descent - metrics.ascent
        val width = paint.measureText(text).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, textHeight.toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -metrics.ascent, paint)

        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (width * height / textHeight).toInt().coerceAtLeast(1),
            height,
            true,
        )
        bitmap.recycle()

        val result = Array(height) { y ->
            BooleanArray(scaled.width) { x ->
                val pixel = scaled.getPixel(x, y)
                Color.alpha(pixel) > 96 && luminance(pixel) > 96
            }
        }
        scaled.recycle()
        return result
    }

    private fun luminance(pixel: Int): Int =
        (Color.red(pixel) * 3 + Color.green(pixel) * 6 + Color.blue(pixel)) / 10
}
