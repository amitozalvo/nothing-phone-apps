package com.amitozalvo.nothingsuite.glyph

/** Rasterized title plus its reading direction, for the marquee. */
class TitleGraphic(
    val rows: Array<BooleanArray>,
    /** RTL text scrolls the other way so it reads start-to-end. */
    val rtl: Boolean,
)

/**
 * Single entry point for turning titles into matrix pixels: the crisp 5×7
 * pixel font when every character is covered (Latin, digits, Hebrew),
 * otherwise the system-font rasterizer.
 */
object TitleRaster {
    fun of(text: String): TitleGraphic = TitleGraphic(
        rows = PixelFont.rasterizeOrNull(text) ?: TextRaster.rasterize(text),
        rtl = text.any { PixelFont.isRtlChar(it) },
    )
}
