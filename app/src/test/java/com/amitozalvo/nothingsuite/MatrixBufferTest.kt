package com.amitozalvo.nothingsuite

import com.amitozalvo.nothingsuite.glyph.DotFont
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MatrixBufferTest {

    @Test
    fun outOfBoundsWritesAreIgnored() {
        val buffer = MatrixBuffer()
        buffer.set(-1, 0, 255)
        buffer.set(0, 25, 255)
        buffer.set(30, 30, 255)
        assertTrue(buffer.pixels.all { it == 0 })
    }

    @Test
    fun brightnessIsClamped() {
        val buffer = MatrixBuffer()
        buffer.set(0, 0, 999)
        assertEquals(255, buffer.get(0, 0))
    }

    @Test
    fun smallTextWidthMatchesDrawnWidth() {
        val buffer = MatrixBuffer()
        val drawn = buffer.smallText(0, 0, "12:34", 255)
        assertEquals(DotFont.smallTextWidth("12:34"), drawn)
    }

    @Test
    fun timeTextFitsMatrix() {
        assertTrue(DotFont.bigTextWidth("23:59") <= MatrixBuffer.SIZE)
    }

    @Test
    fun sixDigitOtpFitsMatrixInSmallFont() {
        assertTrue(DotFont.smallTextWidth("999999") <= MatrixBuffer.SIZE)
    }

    @Test
    fun progressBarFillsProportionally() {
        val buffer = MatrixBuffer()
        buffer.progressBar(1, 22, 23, 3, 0.5f, 255)
        val interior = (0 until 25).count { x -> buffer.get(x, 23) == 255 }
        assertTrue(interior in 9..12)
    }

    @Test
    fun allGlyphsAreConsistentHeight() {
        for (c in "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 :-./+!%'") {
            assertEquals("glyph '$c'", DotFont.SMALL_HEIGHT, DotFont.smallGlyph(c).size)
            val widths = DotFont.smallGlyph(c).map { it.length }.toSet()
            assertEquals("glyph '$c' ragged rows", 1, widths.size)
        }
    }
}
