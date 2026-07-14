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
    fun coversHebrew() {
        assertNotNull(PixelFont.rasterizeOrNull("פגישה עם דנה"))
        assertNotNull(PixelFont.rasterizeOrNull("קניות ב-10:30"))
    }

    @Test
    fun fallsBackForUncoveredScripts() {
        assertNull(PixelFont.rasterizeOrNull("emoji 🎉"))
        assertNull(PixelFont.rasterizeOrNull("日本語"))
    }

    @Test
    fun rtlVisualOrderPutsFirstLetterRightmost() {
        // "אב" logical: alef first. Visually alef must be the RIGHTMOST
        // glyph. Rightmost column pixels should belong to alef's glyph.
        val ab = PixelFont.rasterizeOrNull("אב")!!
        val alef = PixelFont.rasterizeOrNull("א")!!
        val bet = PixelFont.rasterizeOrNull("ב")!!
        val width = ab[0].size
        // Right slice of "אב" == alef, left slice == bet
        for (y in 0 until PixelFont.HEIGHT) {
            for (x in 0 until alef[0].size) {
                assertEquals(alef[y][x], ab[y][width - alef[0].size + x])
            }
            for (x in 0 until bet[0].size) {
                assertEquals(bet[y][x], ab[y][x])
            }
        }
    }

    @Test
    fun ltrRunsInsideRtlTextStayForward() {
        // Digits/latin inside Hebrew must not be mirrored: "10:30" appears
        // as a contiguous forward run inside the visual string raster
        val mixed = PixelFont.rasterizeOrNull("ב 10:30")!!
        val digits = PixelFont.rasterizeOrNull("10:30")!!
        val width = mixed[0].size
        var found = false
        for (offset in 0..(width - digits[0].size)) {
            var match = true
            outer@ for (y in 0 until PixelFont.HEIGHT) {
                for (x in 0 until digits[0].size) {
                    if (digits[y][x] != mixed[y][offset + x]) { match = false; break@outer }
                }
            }
            if (match) { found = true; break }
        }
        assertTrue("digits run should appear un-reversed", found)
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
