package com.amitozalvo.nothingsuite.glyph.scenes

import com.amitozalvo.nothingsuite.config.GlyphSettings
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import com.amitozalvo.nothingsuite.state.ContextSnapshot

/**
 * A persistent full-matrix display. Scenes declare when they are relevant
 * ([isActive]); the engine shows the first active scene in the user's
 * configured order.
 */
interface Scene {
    val id: String

    fun isActive(snapshot: ContextSnapshot, settings: GlyphSettings): Boolean

    /**
     * Render one frame. [tick] increments per animation step while the
     * display is interactive; it stays constant between AOD minute ticks,
     * so scenes must render a meaningful static frame for any tick value.
     */
    fun render(buffer: MatrixBuffer, snapshot: ContextSnapshot, settings: GlyphSettings, tick: Long)
}

/** Shared helpers for marquee text rendering. */
object Marquee {
    /**
     * Draw [graphic] at row [y]. Scrolls when wider than the matrix (or
     * always, with [force]); otherwise centers it. RTL text starts at its
     * beginning (the right end of the raster) and scrolls the other way.
     * Returns true if the text is scrolling (needs animation).
     */
    fun draw(
        buffer: MatrixBuffer,
        y: Int,
        graphic: com.amitozalvo.nothingsuite.glyph.TitleGraphic?,
        tick: Long,
        brightness: Int = 200,
        force: Boolean = false,
    ): Boolean {
        val rows = graphic?.rows ?: return false
        if (rows.isEmpty()) return false
        val width = rows.maxOf { it.size }
        return if (width <= buffer.size && !force) {
            buffer.raster((buffer.size - width) / 2, y, rows, 0, brightness)
            false
        } else {
            // Hold the start readable for a moment, then loop with a gap
            val span = width + GAP
            val progress = (((tick - HOLD_TICKS).coerceAtLeast(0) * STEP) % span).toInt()
            val scroll: Int
            val wrap: Int
            if (graphic.rtl) {
                scroll = (width - buffer.size) - progress
                wrap = scroll + span
            } else {
                scroll = progress
                wrap = scroll - span
            }
            buffer.raster(0, y, rows, scroll, brightness)
            buffer.raster(0, y, rows, wrap, brightness)
            true
        }
    }

    private const val GAP = 10
    private const val STEP = 2
    private const val HOLD_TICKS = 8
}
