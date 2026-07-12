package com.amitozalvo.nothingsuite.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.amitozalvo.nothingsuite.glyph.MatrixBuffer

/**
 * On-screen simulator of the 25×25 Glyph Matrix — lets scenes be designed
 * and verified without pointing a camera at the back of the phone.
 */
@Composable
fun MatrixPreview(
    frame: IntArray,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier
            .background(Color(0xFF0A0A0A), RoundedCornerShape(16.dp))
    ) {
        val n = MatrixBuffer.SIZE
        val cell = size.minDimension / n
        val radius = cell * 0.36f
        for (y in 0 until n) {
            for (x in 0 until n) {
                val v = frame.getOrElse(y * n + x) { 0 }
                val color = if (v == 0) {
                    Color(0xFF161616)
                } else {
                    Color(1f, 1f, 1f, 0.25f + 0.75f * (v / 255f))
                }
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset((x + 0.5f) * cell, (y + 0.5f) * cell),
                )
            }
        }
    }
}
