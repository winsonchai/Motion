package com.winsonchai.motion.core

import kotlin.math.*

enum class YogaPose(val title: String, val setup: String) {
    MOUNTAIN("Mountain", "Face the camera. Stand comfortably tall, feet near hip width, arms relaxed."),
    WARRIOR_II("Warrior II", "Face your chest toward the camera. Widen your stance, turn one foot out, bend that knee and extend both arms. Stay comfortable.")
}

data class Joint(val x: Float, val y: Float, val visibility: Float = 1f)
data class PoseFrame(val joints: List<Joint>, val width: Int, val height: Int)
enum class Tracking { UNKNOWN, ADJUST, MATCH }
data class Assessment(val tracking: Tracking, val cue: String, val angles: String = "")

/** Experimental, view-dependent 2D checks, not a biomechanical or safety assessment. */
object PoseCoach {
    private val required = listOf(0, 11, 12, 13, 14, 15, 16, 23, 24, 25, 26, 27, 28, 29, 30, 31, 32)
    fun angle(a: Joint, b: Joint, c: Joint): Double {
        val ux = (a.x - b.x).toDouble(); val uy = (a.y - b.y).toDouble()
        val vx = (c.x - b.x).toDouble(); val vy = (c.y - b.y).toDouble()
        val denominator = hypot(ux, uy) * hypot(vx, vy)
        if (denominator < 1e-6) return Double.NaN
        return Math.toDegrees(acos(((ux * vx + uy * vy) / denominator).coerceIn(-1.0, 1.0)))
    }

    fun evaluate(frame: PoseFrame?, pose: YogaPose): Assessment {
        fun unknown(cue: String) = Assessment(Tracking.UNKNOWN, cue)
        fun adjust(cue: String, angles: String) = Assessment(Tracking.ADJUST, cue, angles)
        if (frame == null || frame.width <= 0 || frame.height <= 0 || frame.joints.size != 33)
            return unknown("Step into view with your whole body visible")
        if (required.any { i -> frame.joints[i].let {
            !it.x.isFinite() || !it.y.isFinite() || !it.visibility.isFinite() || it.visibility < .65f
        } }) return unknown("Tracking unclear — improve lighting and uncover your joints")
        if (required.any { i -> frame.joints[i].let { it.x !in .02f.. .98f || it.y !in .02f.. .98f } })
            return unknown("Move back — leave space around your head, hands and feet")
        // Convert to pixels before computing angles. Normalized x/y have different scales.
        val p = frame.joints.map { Joint(it.x * frame.width, it.y * frame.height, it.visibility) }
        fun mid(a: Int, b: Int) = Joint((p[a].x + p[b].x) / 2, (p[a].y + p[b].y) / 2)
        val shoulder = mid(11, 12); val hip = mid(23, 24)
        val torso = hypot((shoulder.x - hip.x).toDouble(), (shoulder.y - hip.y).toDouble())
        val shoulderWidth = abs(p[11].x - p[12].x).toDouble()
        if (torso < frame.height * .1 || shoulderWidth < torso * .55 || shoulder.y >= hip.y)
            return unknown("Face your chest toward the camera and keep the phone upright")
        val leftKnee = angle(p[23], p[25], p[27]); val rightKnee = angle(p[24], p[26], p[28])
        val leftElbow = angle(p[11], p[13], p[15]); val rightElbow = angle(p[12], p[14], p[16])
        if (listOf(leftKnee, rightKnee, leftElbow, rightElbow).any { !it.isFinite() })
            return unknown("Tracking unclear — reposition the camera")
        val angles = "Approx. knees L ${leftKnee.roundToInt()}° / R ${rightKnee.roundToInt()}°"
        if (abs(shoulder.x - hip.x) > torso * .18) return adjust("Bring your torso closer to upright", angles)
        if (abs(p[11].y - p[12].y) > shoulderWidth * .22) return adjust("Try to level your shoulders comfortably", angles)
        val stance = abs(p[27].x - p[28].x) / shoulderWidth
        when (pose) {
            YogaPose.MOUNTAIN -> {
                if (stance > 1.4) return adjust("Bring your feet closer to a comfortable hip-width stance", angles)
                if (min(leftKnee, rightKnee) < 158) return adjust("Stand tall without forcing or locking your knees", angles)
                if (p[15].y < p[23].y - torso * .15 || p[16].y < p[24].y - torso * .15 ||
                    min(leftElbow, rightElbow) < 145)
                    return adjust("Let your arms relax down beside your body", angles)
            }
            YogaPose.WARRIOR_II -> {
                if (stance < 1.7) return adjust("Widen your stance only as far as feels comfortable", angles)
                if (min(leftElbow, rightElbow) < 150 ||
                    listOf(13 to 11, 15 to 11, 14 to 12, 16 to 12).any { (a, b) -> abs(p[a].y - p[b].y) > torso * .28 })
                    return adjust("Extend both arms comfortably near shoulder height", angles)
                if (max(leftKnee, rightKnee) < 150) return adjust("Keep your back leg comfortably long", angles)
                if (min(leftKnee, rightKnee) !in 75.0..135.0)
                    return adjust("Check your front-knee bend; avoid forcing a deeper pose", angles)
            }
        }
        return Assessment(Tracking.MATCH, "Within the practice range — breathe naturally", angles)
    }
}

data class HoldState(val currentMs: Long, val bestMs: Long, val goalReached: Boolean)

/** Counts only consecutive matching samples after a stability window. Lost/stale frames reset. */
class HoldTracker(private val settleMs: Long = 600, private val maxGapMs: Long = 500, private val goalMs: Long = 10000) {
    private var lastMs: Long? = null
    private var matchSince: Long? = null
    private var best = 0L
    fun update(nowMs: Long, matching: Boolean): HoldState {
        val last = lastMs
        if (!matching || (last != null && (nowMs <= last || nowMs - last > maxGapMs))) matchSince = null
        lastMs = nowMs
        if (!matching) return HoldState(0, best, false)
        if (matchSince == null) matchSince = nowMs
        val current = (nowMs - matchSince!! - settleMs).coerceAtLeast(0)
        best = max(best, current)
        return HoldState(current, best, current >= goalMs)
    }
    fun reset(clearBest: Boolean = false) {
        lastMs = null; matchSince = null
        if (clearBest) best = 0
    }
}
