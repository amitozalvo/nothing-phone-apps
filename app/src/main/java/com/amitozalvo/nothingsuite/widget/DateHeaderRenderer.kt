package com.amitozalvo.nothingsuite.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import com.amitozalvo.nothingsuite.glyph.DotFont
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Renders the widget's date header ("SAT 12") as a dot-matrix bitmap —
 * Nothing's NDot look without bundling a proprietary font. Weekday in
 * white, day number in Nothing red.
 */
object DateHeaderRenderer {

    private const val WHITE = 0xFFF2F2F2.toInt()
    private const val RED = 0xFFD71921.toInt()
    private const val GREY = 0xFF8A8A8A.toInt()

    fun render(context: Context, date: LocalDate = LocalDate.now()): Bitmap {
        val density = context.resources.displayMetrics.density
        val cell = (4.5f * density)
        val radius = cell * 0.42f

        val weekday = date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .uppercase(Locale.getDefault())
            .filter { DotFont.hasSmallGlyph(it) }
            .ifEmpty { date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() }
        val day = date.dayOfMonth.toString()
        val month = date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())
            .uppercase(Locale.getDefault())
            .filter { DotFont.hasSmallGlyph(it) }
            .ifEmpty { date.month.getDisplayName(TextStyle.SHORT, Locale.ENGLISH).uppercase() }

        val gapDots = 2
        val totalDots = DotFont.smallTextWidth(weekday) + gapDots +
            DotFont.smallTextWidth(day) + gapDots + DotFont.smallTextWidth(month)

        val width = (totalDots * cell).toInt().coerceAtLeast(1)
        val height = (DotFont.SMALL_HEIGHT * cell).toInt().coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.color = WHITE
        var x = drawDotText(canvas, paint, weekday, 0, cell, radius)
        paint.color = RED
        x = drawDotText(canvas, paint, day, x + gapDots, cell, radius)
        paint.color = GREY
        drawDotText(canvas, paint, month, x + gapDots, cell, radius)

        return bitmap
    }

    /** Returns the x (in dots) after the drawn text. */
    private fun drawDotText(
        canvas: Canvas,
        paint: Paint,
        text: String,
        startDot: Int,
        cell: Float,
        radius: Float,
    ): Int {
        var xDot = startDot
        for (c in text) {
            val glyph = DotFont.smallGlyph(c)
            glyph.forEachIndexed { dy, row ->
                row.forEachIndexed { dx, bit ->
                    if (bit == '1') {
                        canvas.drawCircle(
                            (xDot + dx + 0.5f) * cell,
                            (dy + 0.5f) * cell,
                            radius,
                            paint,
                        )
                    }
                }
            }
            xDot += glyph[0].length + 1
        }
        return xDot - 1
    }
}
