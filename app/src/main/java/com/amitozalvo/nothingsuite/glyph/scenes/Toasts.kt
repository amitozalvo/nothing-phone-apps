package com.amitozalvo.nothingsuite.glyph.scenes

import com.amitozalvo.nothingsuite.glyph.DotFont
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer
import com.amitozalvo.nothingsuite.glyph.MatrixIcons
import java.time.Instant

/**
 * A transient takeover of the matrix. Toasts outrank scenes while active;
 * a toast only replaces one of equal or lower [priority].
 */
sealed class MatrixToast {
    abstract val expiresAt: Instant

    /** Higher wins: 1 = battery/feedback, 2 = OTP, 3 = ringing alarm. */
    open val priority: Int = 1

    /** Whether a Glyph Button press dismisses it (alarm snoozes instead). */
    open val dismissableByButton: Boolean = true

    abstract fun render(buffer: MatrixBuffer, tick: Long)

    fun isExpired(now: Instant): Boolean = now >= expiresAt
}

/** Chosen design: code split into rows of large digits, no labels. */
class OtpToast(
    val code: String,
    override val expiresAt: Instant,
    val notificationKey: String?,
) : MatrixToast() {

    override val priority = 2

    override fun render(buffer: MatrixBuffer, tick: Long) {
        when {
            code.length <= 4 -> buffer.bigTextCentered(9, code, 255)
            else -> {
                val split = (code.length + 1) / 2
                buffer.bigTextCentered(4, code.take(split), 255)
                buffer.bigTextCentered(13, code.drop(split), 255)
            }
        }
        buffer.hLine(8, 16, 21, 90)
    }
}

/** Chosen design: battery outline with bolt beside it, percentage below. */
class ChargingToast(
    private val percent: Int,
    override val expiresAt: Instant,
) : MatrixToast() {

    override fun render(buffer: MatrixBuffer, tick: Long) {
        drawBattery(buffer, x = 6, y = 4, percent = percent)
        buffer.sprite(17, 4, MatrixIcons.LIGHTNING, 255)
        drawPercent(buffer, y = 13, percent = percent)
    }
}

class LowBatteryToast(
    private val percent: Int,
    override val expiresAt: Instant,
) : MatrixToast() {

    override fun render(buffer: MatrixBuffer, tick: Long) {
        buffer.smallTextCentered(1, "LOW", 160)
        drawBattery(buffer, x = 8, y = 8, percent = percent)
        drawPercent(buffer, y = 16, percent = percent)
    }
}

/**
 * A firing alarm: pulsing clock, current alarm ringing. Not dismissable —
 * the Glyph Button snoozes it via the notification's snooze action.
 */
class AlarmRingingToast(
    val notificationKey: String,
) : MatrixToast() {

    override val expiresAt: Instant = Instant.MAX
    override val priority = 3
    override val dismissableByButton = false

    override fun render(buffer: MatrixBuffer, tick: Long) {
        val pulse = (tick / 4) % 2 == 0L
        buffer.sprite((buffer.size - 7) / 2, 3, MatrixIcons.ALARM, if (pulse) 255 else 130)
        buffer.smallTextCentered(13, "ZZZ", 140)
        buffer.hLine(8, 16, 19, if (pulse) 90 else 40)
    }
}

/** Short confirmation flash ("PLAY", "PAUSE", "ZZZ"). */
class TextToast(
    private val text: String,
    override val expiresAt: Instant,
) : MatrixToast() {

    override fun render(buffer: MatrixBuffer, tick: Long) {
        buffer.smallTextCentered(10, text, 255)
    }
}

private fun drawBattery(buffer: MatrixBuffer, x: Int, y: Int, percent: Int) {
    buffer.sprite(x, y, MatrixIcons.BATTERY, 255)
    val fill = (7 * percent.coerceIn(0, 100)) / 100
    for (col in 0 until fill) {
        buffer.vLine(x + 1 + col, y + 1, y + 3, 255)
    }
}

private fun drawPercent(buffer: MatrixBuffer, y: Int, percent: Int) {
    val digits = percent.coerceIn(0, 100).toString()
    val width = DotFont.bigTextWidth(digits) + 2 + DotFont.smallTextWidth("%")
    var x = (buffer.size - width) / 2
    x += buffer.bigText(x, y, digits, 255) + 2
    buffer.smallText(x, y + 1, "%", 140)
}
