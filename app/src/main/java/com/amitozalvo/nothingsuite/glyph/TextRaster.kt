package com.amitozalvo.nothingsuite.glyph

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Fallback rasterizer for text the 5×7 PixelFont can't cover (Hebrew and
 * other scripts). Renders the system font with anti-aliasing OFF at a
 * small size — hard 1-bit edges survive on LEDs where thresholded
 * anti-aliased strokes break up.
 */
object TextRaster {

    fun rasterize(text: String, height: Int = 9): Array<BooleanArray> {
        if (text.isBlank()) return emptyArray()

        val paint = Paint().apply {
            isAntiAlias = false
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = height * 1.2f
            color = Color.WHITE
        }
        val metrics = paint.fontMetrics
        val bmpHeight = (metrics.descent - metrics.ascent).toInt().coerceAtLeast(1)
        val width = paint.measureText(text).toInt().coerceAtLeast(1)

        val bitmap = Bitmap.createBitmap(width, bmpHeight, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).drawText(text, 0f, -metrics.ascent, paint)

        val grid = Array(bmpHeight) { y ->
            BooleanArray(width) { x -> Color.alpha(bitmap.getPixel(x, y)) > 0 }
        }
        bitmap.recycle()

        // Trim empty rows so the visible glyphs use the full budget
        val content = grid.dropWhile { row -> row.none { it } }
            .dropLastWhile { row -> row.none { it } }
            .ifEmpty { return emptyArray() }

        if (content.size <= height + 1) return content.toTypedArray()

        // Nearest-neighbour vertical squeeze as a last resort
        return Array(height) { y ->
            content[y * content.size / height]
        }
    }
}
