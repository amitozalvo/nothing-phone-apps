package com.amitozalvo.nothingsuite

import com.amitozalvo.nothingsuite.glyph.PixelFont
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PixelFontTest {

    @Test
    fun coversLatinTitles() {
        assertNotNull(PixelFont.rasterizeOrNull("Design sync"))
        assertNotNull(PixelFont.rasterizeOrNull("1:1 with Dana - Q3 plan?"))
    }

    @Test
    fun fallsBackForUncoveredScripts() {
        assertNull(PixelFont.rasterizeOrNull("פגישה"))
        assertNull(PixelFont.rasterizeOrNull("emoji 🎉"))
    }

    @Test
    fun rasterIsSevenRowsAndConsistent() {
        val raster = PixelFont.rasterizeOrNull("ABC 123")!!
        assertEquals(PixelFont.HEIGHT, raster.size)
        val widths = raster.map { it.size }.toSet()
        assertEquals(1, widths.size)
        assertTrue(raster.any { row -> row.any { it } })
    }
}
