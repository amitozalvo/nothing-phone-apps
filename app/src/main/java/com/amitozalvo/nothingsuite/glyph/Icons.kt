package com.amitozalvo.nothingsuite.glyph

/** Small icon sprites for the Glyph Matrix ('1' = lit). */
object MatrixIcons {

    val CALENDAR = listOf(
        "11111",
        "10101",
        "11111",
        "10101",
        "11111",
    )

    val BELL = listOf(
        "00100",
        "01110",
        "01110",
        "11111",
        "00100",
    )

    val LIGHTNING = listOf(
        "001",
        "010",
        "111",
        "010",
        "100",
    )

    // 9×5 battery outline; interior (7 cols) is filled by charge level
    val BATTERY = listOf(
        "111111110",
        "100000011",
        "100000011",
        "100000011",
        "111111110",
    )

    // Clock face with hands at ~3 o'clock
    val ALARM = listOf(
        "0011100",
        "0100010",
        "1001001",
        "1001101",
        "1000001",
        "0100010",
        "0011100",
    )

    val NOTE = listOf(
        "01111",
        "01001",
        "01001",
        "11011",
        "11011",
    )

    val MESSAGE = listOf(
        "1111111",
        "1000001",
        "1000001",
        "1111111",
        "0110000",
    )
}
