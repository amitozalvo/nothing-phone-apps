package com.amitozalvo.nothingsuite.glyph

import kotlin.math.PI
import kotlin.math.atan2

/**
 * A 25×25 brightness buffer for the Glyph Matrix. Values 0–255.
 * Pure Kotlin so scene rendering is unit-testable on the JVM.
 */
class MatrixBuffer(val size: Int = SIZE) {

    val pixels = IntArray(size * size)

    fun clear() = pixels.fill(0)

    fun set(x: Int, y: Int, brightness: Int) {
        if (x in 0 until size && y in 0 until size) {
            pixels[y * size + x] = brightness.coerceIn(0, 255)
        }
    }

    fun get(x: Int, y: Int): Int =
        if (x in 0 until size && y in 0 until size) pixels[y * size + x] else 0

    fun hLine(x0: Int, x1: Int, y: Int, brightness: Int) {
        for (x in minOf(x0, x1)..maxOf(x0, x1)) set(x, y, brightness)
    }

    fun vLine(x: Int, y0: Int, y1: Int, brightness: Int) {
        for (y in minOf(y0, y1)..maxOf(y0, y1)) set(x, y, brightness)
    }

    fun rect(x: Int, y: Int, w: Int, h: Int, brightness: Int, fill: Boolean = false) {
        if (fill) {
            for (yy in y until y + h) hLine(x, x + w - 1, yy, brightness)
        } else {
            hLine(x, x + w - 1, y, brightness)
            hLine(x, x + w - 1, y + h - 1, brightness)
            vLine(x, y, y + h - 1, brightness)
            vLine(x + w - 1, y, y + h - 1, brightness)
        }
    }

    /** Draw a '1'/'0' string-row pattern with its top-left at (x, y). */
    fun sprite(x: Int, y: Int, pattern: List<String>, brightness: Int) {
        pattern.forEachIndexed { dy, row ->
            row.forEachIndexed { dx, c ->
                if (c == '1') set(x + dx, y + dy, brightness)
            }
        }
    }

    /** 3×5 text. Returns the width drawn. */
    fun smallText(x: Int, y: Int, text: String, brightness: Int, spacing: Int = 1): Int {
        var cx = x
        for (c in text) {
            val glyph = DotFont.smallGlyph(c)
            sprite(cx, y, glyph, brightness)
            cx += glyph[0].length + spacing
        }
        return cx - x - spacing
    }

    /** 4×6 digit text (time / counters). Returns the width drawn. */
    fun bigText(x: Int, y: Int, text: String, brightness: Int, spacing: Int = 1): Int {
        var cx = x
        for (c in text) {
            val glyph = DotFont.bigGlyph(c)
            sprite(cx, y, glyph, brightness)
            cx += glyph[0].length + spacing
        }
        return cx - x - spacing
    }

    fun smallTextCentered(y: Int, text: String, brightness: Int) {
        smallText((size - DotFont.smallTextWidth(text)) / 2, y, text, brightness)
    }

    fun bigTextCentered(y: Int, text: String, brightness: Int) {
        bigText((size - DotFont.bigTextWidth(text)) / 2, y, text, brightness)
    }

    /**
     * Horizontal progress bar with a 1px outline. progress in [0, 1] fills
     * the interior from the left.
     */
    fun progressBar(x: Int, y: Int, w: Int, h: Int, progress: Float, brightness: Int) {
        rect(x, y, w, h, brightness / 2)
        val innerW = w - 2
        val filled = (innerW * progress.coerceIn(0f, 1f)).toInt()
        for (yy in y + 1 until y + h - 1) {
            if (filled > 0) hLine(x + 1, x + filled, yy, brightness)
        }
    }

    /**
     * Circular progress ring hugging the (physically round) matrix edge,
     * clockwise from 12 o'clock. [progress] in [0, 1] lights the travelled
     * arc bright over a dim track. Uses midpoint-circle rasterization for
     * a clean, symmetric circle (angle sampling wobbles).
     */
    fun ring(progress: Float, track: Int, fill: Int) {
        val c = (size - 1) / 2
        var x = c
        var y = 0
        var err = 1 - c
        val points = mutableSetOf<Pair<Int, Int>>()
        while (x >= y) {
            points += listOf(
                c + x to c + y, c - x to c + y, c + x to c - y, c - x to c - y,
                c + y to c + x, c - y to c + x, c + y to c - x, c - y to c - x,
            )
            y++
            if (err < 0) err += 2 * y + 1 else { x--; err += 2 * (y - x) + 1 }
        }
        val cutoff = 2.0 * PI * progress.coerceIn(0f, 1f)
        for ((px, py) in points) {
            // Clockwise angle from 12 o'clock
            var angle = atan2((px - c).toDouble(), (c - py).toDouble())
            if (angle < 0) angle += 2.0 * PI
            set(px, py, if (angle < cutoff) fill else track)
        }
    }

    /**
     * Zero out pixels outside the physically round LED area. The Phone (3)
     * matrix is a circle inscribed in the 25×25 grid — corner cells have
     * no LEDs. Called by the engine on every finished frame.
     */
    fun maskCircle() {
        for (y in 0 until size) {
            for (x in 0 until size) {
                if (!inCircle(x, y, size)) pixels[y * size + x] = 0
            }
        }
    }

    /**
     * Draw a boolean raster (e.g. rasterized unicode text) clipped to the
     * matrix, offset horizontally by [scrollX] for marquee scrolling.
     */
    fun raster(xOnMatrix: Int, y: Int, data: Array<BooleanArray>, scrollX: Int, brightness: Int) {
        data.forEachIndexed { dy, row ->
            for (dx in row.indices) {
                if (row[dx]) set(xOnMatrix + dx - scrollX, y + dy, brightness)
            }
        }
    }

    fun snapshot(): IntArray = pixels.copyOf()

    companion object {
        const val SIZE = 25

        /** Whether the grid cell physically exists on the round matrix. */
        fun inCircle(x: Int, y: Int, size: Int = SIZE): Boolean {
            val c = (size - 1) / 2.0
            val dx = x - c
            val dy = y - c
            return dx * dx + dy * dy <= c * c + size / 2.0
        }
    }
}
