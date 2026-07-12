package com.amitozalvo.nothingsuite.glyph

/**
 * Classic 5×7 LED-marquee font — hand-built pixels, so titles stay crisp
 * on the matrix where downscaled vector fonts turn to mush. Pure Kotlin.
 * Text with characters outside this set falls back to system-font
 * rasterization (TextRaster).
 */
object PixelFont {

    const val HEIGHT = 7

    private val glyphs: Map<Char, List<String>> = mapOf(
        'A' to listOf("01110", "10001", "10001", "11111", "10001", "10001", "10001"),
        'B' to listOf("11110", "10001", "10001", "11110", "10001", "10001", "11110"),
        'C' to listOf("01110", "10001", "10000", "10000", "10000", "10001", "01110"),
        'D' to listOf("11100", "10010", "10001", "10001", "10001", "10010", "11100"),
        'E' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "11111"),
        'F' to listOf("11111", "10000", "10000", "11110", "10000", "10000", "10000"),
        'G' to listOf("01110", "10001", "10000", "10111", "10001", "10001", "01111"),
        'H' to listOf("10001", "10001", "10001", "11111", "10001", "10001", "10001"),
        'I' to listOf("01110", "00100", "00100", "00100", "00100", "00100", "01110"),
        'J' to listOf("00111", "00010", "00010", "00010", "00010", "10010", "01100"),
        'K' to listOf("10001", "10010", "10100", "11000", "10100", "10010", "10001"),
        'L' to listOf("10000", "10000", "10000", "10000", "10000", "10000", "11111"),
        'M' to listOf("10001", "11011", "10101", "10101", "10001", "10001", "10001"),
        'N' to listOf("10001", "10001", "11001", "10101", "10011", "10001", "10001"),
        'O' to listOf("01110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'P' to listOf("11110", "10001", "10001", "11110", "10000", "10000", "10000"),
        'Q' to listOf("01110", "10001", "10001", "10001", "10101", "10010", "01101"),
        'R' to listOf("11110", "10001", "10001", "11110", "10100", "10010", "10001"),
        'S' to listOf("01111", "10000", "10000", "01110", "00001", "00001", "11110"),
        'T' to listOf("11111", "00100", "00100", "00100", "00100", "00100", "00100"),
        'U' to listOf("10001", "10001", "10001", "10001", "10001", "10001", "01110"),
        'V' to listOf("10001", "10001", "10001", "10001", "10001", "01010", "00100"),
        'W' to listOf("10001", "10001", "10001", "10101", "10101", "10101", "01010"),
        'X' to listOf("10001", "10001", "01010", "00100", "01010", "10001", "10001"),
        'Y' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        'Z' to listOf("11111", "00001", "00010", "00100", "01000", "10000", "11111"),
        '0' to listOf("01110", "10001", "10011", "10101", "11001", "10001", "01110"),
        '1' to listOf("00100", "01100", "00100", "00100", "00100", "00100", "01110"),
        '2' to listOf("01110", "10001", "00001", "00010", "00100", "01000", "11111"),
        '3' to listOf("11111", "00010", "00100", "00010", "00001", "10001", "01110"),
        '4' to listOf("00010", "00110", "01010", "10010", "11111", "00010", "00010"),
        '5' to listOf("11111", "10000", "11110", "00001", "00001", "10001", "01110"),
        '6' to listOf("00110", "01000", "10000", "11110", "10001", "10001", "01110"),
        '7' to listOf("11111", "00001", "00010", "00100", "01000", "01000", "01000"),
        '8' to listOf("01110", "10001", "10001", "01110", "10001", "10001", "01110"),
        '9' to listOf("01110", "10001", "10001", "01111", "00001", "00010", "01100"),
        ' ' to listOf("000", "000", "000", "000", "000", "000", "000"),
        '-' to listOf("000", "000", "000", "111", "000", "000", "000"),
        '.' to listOf("0", "0", "0", "0", "0", "0", "1"),
        ',' to listOf("00", "00", "00", "00", "00", "01", "10"),
        ':' to listOf("0", "0", "1", "0", "1", "0", "0"),
        '!' to listOf("1", "1", "1", "1", "1", "0", "1"),
        '?' to listOf("01110", "10001", "00001", "00010", "00100", "00000", "00100"),
        '\'' to listOf("1", "1", "0", "0", "0", "0", "0"),
        '/' to listOf("00001", "00001", "00010", "00100", "01000", "10000", "10000"),
        '(' to listOf("01", "10", "10", "10", "10", "10", "01"),
        ')' to listOf("10", "01", "01", "01", "01", "01", "10"),
        '+' to listOf("00000", "00100", "00100", "11111", "00100", "00100", "00000"),
        '&' to listOf("01100", "10010", "10100", "01000", "10101", "10010", "01101"),
        '@' to listOf("01110", "10001", "10111", "10101", "10111", "10000", "01110"),
        '#' to listOf("01010", "01010", "11111", "01010", "11111", "01010", "01010"),
        // Hebrew alphabet (block-letter approximations, incl. final forms)
        'א' to listOf("10010", "10010", "01100", "00100", "00110", "01001", "01001"),
        'ב' to listOf("01110", "00010", "00010", "00010", "00010", "00010", "11111"),
        'ג' to listOf("01110", "00010", "00010", "00010", "00110", "01010", "10010"),
        'ד' to listOf("11111", "00010", "00010", "00010", "00010", "00010", "00010"),
        'ה' to listOf("11111", "00001", "00001", "10001", "10001", "10001", "10001"),
        'ו' to listOf("011", "001", "001", "001", "001", "001", "001"),
        'ז' to listOf("111", "010", "010", "010", "010", "010", "010"),
        'ח' to listOf("11111", "10001", "10001", "10001", "10001", "10001", "10001"),
        'ט' to listOf("10011", "10101", "10001", "10001", "10001", "10001", "01110"),
        'י' to listOf("11", "01", "01", "00", "00", "00", "00"),
        'כ' to listOf("11110", "00001", "00001", "00001", "00001", "00001", "11110"),
        'ך' to listOf("11110", "00001", "00001", "00001", "00001", "00001", "00001"),
        'ל' to listOf("10000", "01000", "01110", "00010", "00010", "00010", "01100"),
        'מ' to listOf("01110", "10001", "10101", "10101", "10001", "10001", "10110"),
        'ם' to listOf("11111", "10001", "10001", "10001", "10001", "10001", "11111"),
        'נ' to listOf("110", "010", "010", "010", "010", "010", "111"),
        'ן' to listOf("11", "01", "01", "01", "01", "01", "01"),
        'ס' to listOf("11110", "10001", "10001", "10001", "10001", "10001", "01110"),
        'ע' to listOf("10001", "10001", "01001", "01010", "00100", "01000", "10000"),
        'פ' to listOf("11110", "00001", "01101", "01001", "00001", "00001", "11110"),
        'ף' to listOf("11110", "00001", "01101", "01001", "00001", "00001", "00001"),
        'צ' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "01110"),
        'ץ' to listOf("10001", "10001", "01010", "00100", "00100", "00100", "00100"),
        'ק' to listOf("11111", "10001", "10001", "10001", "10000", "10000", "10000"),
        'ר' to listOf("11111", "00001", "00001", "00001", "00001", "00001", "00001"),
        'ש' to listOf("10101", "10101", "10101", "10101", "10101", "10001", "01110"),
        'ת' to listOf("11111", "01001", "01001", "01001", "01001", "01001", "11001"),
    )

    fun isRtlChar(c: Char): Boolean = c in '֐'..'׿' || c in '؀'..'ۿ'

    fun supports(text: String): Boolean =
        text.all { it.uppercaseChar() in glyphs }

    /**
     * Rasterize [text] to a 7-row boolean grid, or null if any character
     * has no pixel glyph (caller falls back to system-font rasterization).
     * Text containing RTL characters is laid out in visual order (simple
     * BiDi: runs placed right-to-left, LTR runs like digits kept intact).
     */
    fun rasterizeOrNull(text: String): Array<BooleanArray>? {
        if (text.isEmpty() || !supports(text)) return null
        val visual = if (text.any { isRtlChar(it) }) toVisualRtl(text) else text
        val glyphList = visual.map { glyphs.getValue(it.uppercaseChar()) }
        val width = glyphList.sumOf { it[0].length + 1 } - 1
        val rows = Array(HEIGHT) { BooleanArray(width) }
        var x = 0
        for (glyph in glyphList) {
            glyph.forEachIndexed { y, row ->
                row.forEachIndexed { dx, c ->
                    if (c == '1') rows[y][x + dx] = true
                }
            }
            x += glyph[0].length + 1
        }
        return rows
    }

    /**
     * Left-to-right visual form of RTL-base text: runs reversed, RTL runs
     * character-reversed, LTR runs (digits, Latin) kept as-is. Neutrals
     * join the run in progress.
     */
    private fun toVisualRtl(text: String): String {
        data class Run(val rtl: Boolean, val chars: StringBuilder)
        val runs = mutableListOf<Run>()
        for (c in text) {
            val strongRtl = isRtlChar(c)
            val strongLtr = c.isLetterOrDigit() && !strongRtl
            val last = runs.lastOrNull()
            when {
                last == null -> runs += Run(strongRtl, StringBuilder().append(c))
                !strongRtl && !strongLtr -> last.chars.append(c) // neutral
                last.rtl == strongRtl -> last.chars.append(c)
                else -> runs += Run(strongRtl, StringBuilder().append(c))
            }
        }
        return runs.reversed().joinToString("") { run ->
            if (run.rtl) run.chars.reverse().toString() else run.chars.toString()
        }
    }
}
