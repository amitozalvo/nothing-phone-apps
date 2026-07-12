package com.amitozalvo.nothingsuite.glyph.scenes

import com.amitozalvo.nothingsuite.glyph.DotFont
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import com.amitozalvo.nothingsuite.glyph.MatrixIcons
import java.time.Instant

/**
 * A transient takeover of the matrix. Toasts outrank scenes while active,
 * then the display reverts to the active scene.
 */
sealed class MatrixToast {
    abstract val expiresAt: Instant

    /** OTP persists until Glyph Button press or expiry; others just expire. */
    open val dismissableByButton: Boolean = false

    abstract fun render(buffer: MatrixBuffer, tick: Long)

    fun isExpired(now: Instant): Boolean = now >= expiresAt
}

class OtpToast(
    val code: String,
    override val expiresAt: Instant,
    val notificationKey: String?,
) : MatrixToast() {

    override val dismissableByButton = true

    override fun render(buffer: MatrixBuffer, tick: Long) {
        buffer.smallTextCentered(0, "CODE", 120)
        when {
            code.length <= 4 -> buffer.bigTextCentered(9, code, 255)
            code.length <= 6 -> buffer.smallTextCentered(10, code, 255)
            else -> {
                val split = (code.length + 1) / 2
                buffer.smallTextCentered(8, code.take(split), 255)
                buffer.smallTextCentered(15, code.drop(split), 255)
            }
        }
        buffer.hLine(4, buffer.size - 5, buffer.size - 2, 80)
    }
}

class ChargingToast(
    private val percent: Int,
    override val expiresAt: Instant,
) : MatrixToast() {

    override fun render(buffer: MatrixBuffer, tick: Long) {
        drawBattery(buffer, y = 5, percent = percent, brightness = 255)
        buffer.sprite(11, 1, MatrixIcons.LIGHTNING, 255)
        drawPercent(buffer, y = 14, percent = percent)
    }
}

class LowBatteryToast(
    private val percent: Int,
    override val expiresAt: Instant,
) : MatrixToast() {

    override fun render(buffer: MatrixBuffer, tick: Long) {
        buffer.smallTextCentered(0, "LOW", 200)
        drawBattery(buffer, y = 8, percent = percent, brightness = 255)
        drawPercent(buffer, y = 17, percent = percent)
    }
}

private fun drawBattery(buffer: MatrixBuffer, y: Int, percent: Int, brightness: Int) {
    val x = (buffer.size - 9) / 2
    buffer.sprite(x, y, MatrixIcons.BATTERY, brightness)
    val fill = (7 * percent.coerceIn(0, 100)) / 100
    for (col in 0 until fill) {
        buffer.vLine(x + 1 + col, y + 1, y + 3, brightness)
    }
}

private fun drawPercent(buffer: MatrixBuffer, y: Int, percent: Int) {
    val digits = percent.coerceIn(0, 100).toString()
    val width = DotFont.bigTextWidth(digits) + 1 + DotFont.smallTextWidth("%")
    var x = (buffer.size - width) / 2
    x += buffer.bigText(x, y, digits, 255) + 1
    buffer.smallText(x, y + 1, "%", 140)
}
