package com.amitozalvo.nothingsuite.glyph

/**
 * Single entry point for turning titles into matrix pixels: the crisp 5×7
 * pixel font when every character is covered, otherwise the system-font
 * rasterizer (for Hebrew and anything else).
 */
object TitleRaster {
    fun of(text: String): Array<BooleanArray> =
        PixelFont.rasterizeOrNull(text) ?: TextRaster.rasterize(text)
}
