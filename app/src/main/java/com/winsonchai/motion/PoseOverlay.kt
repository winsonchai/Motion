package com.winsonchai.motion

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import com.winsonchai.motion.core.PoseFrame
import kotlin.math.min

class PoseOverlay(context: Context) : View(context) {
    var frame: PoseFrame? = null
    var mirrored = true
    var matched = false
    private val pen = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeWidth = 3 * resources.displayMetrics.density }
    private val links = listOf(11 to 12, 11 to 13, 13 to 15, 12 to 14, 14 to 16,
        11 to 23, 12 to 24, 23 to 24, 23 to 25, 25 to 27, 24 to 26, 26 to 28,
        27 to 29, 29 to 31, 27 to 31, 28 to 30, 30 to 32, 28 to 32)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val f = frame ?: return
        if (f.width <= 0 || f.height <= 0 || f.joints.size != 33) return
        val scale = min(width.toFloat() / f.width, height.toFloat() / f.height)
        val dx = (width - f.width * scale) / 2
        val dy = (height - f.height * scale) / 2
        fun x(i: Int) = dx + (if (mirrored) 1 - f.joints[i].x else f.joints[i].x) * f.width * scale
        fun y(i: Int) = dy + f.joints[i].y * f.height * scale
        fun visible(i: Int) = f.joints[i].visibility >= .65f && f.joints[i].x.isFinite() && f.joints[i].y.isFinite()
        pen.color = if (matched) Color.rgb(95, 225, 180) else Color.rgb(255, 202, 112)
        for ((a, b) in links) if (visible(a) && visible(b)) canvas.drawLine(x(a), y(a), x(b), y(b), pen)
        for (i in 11..32) if (visible(i)) canvas.drawCircle(x(i), y(i), 4 * resources.displayMetrics.density, pen)
    }
}
