package com.amitozalvo.nothingsuite.glyph

/**
 * Tiny bitmap fonts rendered as dot patterns. Pure Kotlin — shared by the
 * Glyph Matrix renderer (1 dot = 1 LED) and the widget date header
 * (1 dot = 1 drawn circle).
 *
 * Patterns are rows of '1'/'0'. All glyphs in a font share a height;
 * widths may vary per glyph.
 */
object DotFont {

    const val SMALL_HEIGHT = 5
    const val BIG_HEIGHT = 6

    /** 3×5 font for A–Z, 0–9 and a few symbols. */
    private val small: Map<Char, List<String>> = mapOf(
        'A' to listOf("010", "101", "111", "101", "101"),
        'B' to listOf("110", "101", "110", "101", "110"),
        'C' to listOf("011", "100", "100", "100", "011"),
        'D' to listOf("110", "101", "101", "101", "110"),
        'E' to listOf("111", "100", "110", "100", "111"),
        'F' to listOf("111", "100", "110", "100", "100"),
        'G' to listOf("011", "100", "101", "101", "011"),
        'H' to listOf("101", "101", "111", "101", "101"),
        'I' to listOf("111", "010", "010", "010", "111"),
        'J' to listOf("001", "001", "001", "101", "010"),
        'K' to listOf("101", "110", "100", "110", "101"),
        'L' to listOf("100", "100", "100", "100", "111"),
        'M' to listOf("101", "111", "111", "101", "101"),
        'N' to listOf("110", "101", "101", "101", "101"),
        'O' to listOf("010", "101", "101", "101", "010"),
        'P' to listOf("110", "101", "110", "100", "100"),
        'Q' to listOf("010", "101", "101", "110", "011"),
        'R' to listOf("110", "101", "110", "110", "101"),
        'S' to listOf("011", "100", "010", "001", "110"),
        'T' to listOf("111", "010", "010", "010", "010"),
        'U' to listOf("101", "101", "101", "101", "111"),
        'V' to listOf("101", "101", "101", "101", "010"),
        'W' to listOf("101", "101", "111", "111", "101"),
        'X' to listOf("101", "101", "010", "101", "101"),
        'Y' to listOf("101", "101", "010", "010", "010"),
        'Z' to listOf("111", "001", "010", "100", "111"),
        '0' to listOf("010", "101", "101", "101", "010"),
        '1' to listOf("010", "110", "010", "010", "111"),
        '2' to listOf("110", "001", "010", "100", "111"),
        '3' to listOf("110", "001", "010", "001", "110"),
        '4' to listOf("101", "101", "111", "001", "001"),
        '5' to listOf("111", "100", "110", "001", "110"),
        '6' to listOf("011", "100", "110", "101", "010"),
        '7' to listOf("111", "001", "010", "010", "010"),
        '8' to listOf("010", "101", "010", "101", "010"),
        '9' to listOf("010", "101", "011", "001", "110"),
        ' ' to listOf("00", "00", "00", "00", "00"),
        ':' to listOf("0", "1", "0", "1", "0"),
        '-' to listOf("000", "000", "111", "000", "000"),
        '.' to listOf("0", "0", "0", "0", "1"),
        '/' to listOf("001", "001", "010", "100", "100"),
        '+' to listOf("000", "010", "111", "010", "000"),
        '!' to listOf("1", "1", "1", "0", "1"),
        '%' to listOf("11001", "11010", "00100", "01011", "10011"),
        '\'' to listOf("1", "1", "0", "0", "0"),
    )

    /** 4×6 digits for large time display. */
    private val bigDigits: Map<Char, List<String>> = mapOf(
        '0' to listOf("0110", "1001", "1001", "1001", "1001", "0110"),
        '1' to listOf("0010", "0110", "0010", "0010", "0010", "0111"),
        '2' to listOf("0110", "1001", "0010", "0100", "1000", "1111"),
        '3' to listOf("0110", "1001", "0010", "0001", "1001", "0110"),
        '4' to listOf("0011", "0101", "1001", "1111", "0001", "0001"),
        '5' to listOf("1111", "1000", "1110", "0001", "1001", "0110"),
        '6' to listOf("0110", "1000", "1110", "1001", "1001", "0110"),
        '7' to listOf("1111", "0001", "0010", "0010", "0100", "0100"),
        '8' to listOf("0110", "1001", "0110", "1001", "1001", "0110"),
        '9' to listOf("0110", "1001", "1001", "0111", "0001", "0110"),
        ':' to listOf("0", "1", "0", "0", "1", "0"),
        ' ' to listOf("00", "00", "00", "00", "00", "00"),
    )

    private val smallFallback = listOf("111", "111", "111", "111", "111")

    fun smallGlyph(c: Char): List<String> =
        small[c.uppercaseChar()] ?: smallFallback

    fun hasSmallGlyph(c: Char): Boolean = small.containsKey(c.uppercaseChar())

    fun bigGlyph(c: Char): List<String> = bigDigits[c] ?: smallFallback

    fun smallTextWidth(text: String, spacing: Int = 1): Int {
        if (text.isEmpty()) return 0
        return text.sumOf { smallGlyph(it)[0].length + spacing } - spacing
    }

    fun bigTextWidth(text: String, spacing: Int = 1): Int {
        if (text.isEmpty()) return 0
        return text.sumOf { bigGlyph(it)[0].length + spacing } - spacing
    }
}
