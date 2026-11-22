package com.winsonchai.motion.core

import org.junit.Assert.*
import org.junit.Test

class PoseCoachTest {
    private fun mountain(): PoseFrame {
        val p = MutableList(33) { Joint(.5f, .2f) }
        fun set(i: Int, x: Float, y: Float) { p[i] = Joint(x, y) }
        set(0, .5f, .12f)
        set(11, .4f, .3f); set(12, .6f, .3f)
        set(13, .37f, .43f); set(14, .63f, .43f)
        set(15, .35f, .55f); set(16, .65f, .55f)
        set(23, .44f, .5f); set(24, .56f, .5f)
        set(25, .44f, .68f); set(26, .56f, .68f)
        set(27, .44f, .85f); set(28, .56f, .85f)
        set(29, .43f, .86f); set(30, .57f, .86f)
        set(31, .42f, .89f); set(32, .58f, .89f)
        return PoseFrame(p, 1000, 1000)
    }
    private fun mutate(frame: PoseFrame, i: Int, joint: Joint) = frame.copy(joints = frame.joints.toMutableList().apply { set(i, joint) })
    @Test fun mountainMatchesAndBentKneeDoesNot() {
        assertEquals(Tracking.MATCH, PoseCoach.evaluate(mountain(), YogaPose.MOUNTAIN).tracking)
        val bent = mutate(mountain(), 25, Joint(.3f, .65f))
        assertEquals(Tracking.ADJUST, PoseCoach.evaluate(bent, YogaPose.MOUNTAIN).tracking)
    }
    @Test fun warriorNeedsBothArmsAndDistinctKneeBends() {
        val p = mountain().joints.toMutableList()
        p[13] = Joint(.25f, .3f); p[14] = Joint(.75f, .3f)
        p[15] = Joint(.1f, .3f); p[16] = Joint(.9f, .3f)
        p[25] = Joint(.26f, .5f); p[27] = Joint(.26f, .82f)
        p[26] = Joint(.65f, .67f); p[28] = Joint(.74f, .84f)
        val f = PoseFrame(p, 1000, 1000)
        assertEquals(Tracking.MATCH, PoseCoach.evaluate(f, YogaPose.WARRIOR_II).tracking)
        assertEquals(Tracking.ADJUST, PoseCoach.evaluate(mutate(f, 16, Joint(.75f, .6f)), YogaPose.WARRIOR_II).tracking)
        assertEquals(Tracking.ADJUST, PoseCoach.evaluate(mountain(), YogaPose.WARRIOR_II).tracking)
    }
    @Test fun missingOccludedClippedAndNonFiniteJointsAreUnknown() {
        assertEquals(Tracking.UNKNOWN, PoseCoach.evaluate(null, YogaPose.MOUNTAIN).tracking)
        listOf(Joint(.4f, .3f, .1f), Joint(0f, .3f), Joint(Float.NaN, .3f), Joint(.4f, .3f, Float.NaN)).forEach {
            assertEquals(Tracking.UNKNOWN, PoseCoach.evaluate(mutate(mountain(), 11, it), YogaPose.MOUNTAIN).tracking)
        }
    }
    @Test fun sidewaysViewIsNotCoachedAsFrontView() {
        val f = mutate(mountain(), 12, Joint(.41f, .3f))
        assertEquals(Tracking.UNKNOWN, PoseCoach.evaluate(f, YogaPose.MOUNTAIN).tracking)
    }
    @Test fun imageAspectRatioDoesNotDistortTheSameGeometry() {
        val a = mountain()
        val b = a.copy(width = 2000, joints = a.joints.map { it.copy(x = it.x / 2) })
        assertEquals(PoseCoach.evaluate(a, YogaPose.MOUNTAIN), PoseCoach.evaluate(b, YogaPose.MOUNTAIN))
        assertEquals(90.0, PoseCoach.angle(Joint(1f, 0f), Joint(0f, 0f), Joint(0f, 1f)), .001)
        assertTrue(PoseCoach.angle(Joint(0f, 0f), Joint(0f, 0f), Joint(1f, 0f)).isNaN())
    }
    @Test fun timerSettlesAndCountsConsecutiveSamplesOnly() {
        val t = HoldTracker()
        for (now in 0L..1000L step 100) t.update(now, true)
        assertEquals(500L, t.update(1100, true).currentMs)
        val lost = t.update(1200, false)
        assertEquals(0L, lost.currentMs); assertEquals(500L, lost.bestMs)
        assertEquals(0L, t.update(1300, true).currentMs)
    }
    @Test fun staleReversedAndPausedTimeNeverAddsToHold() {
        val t = HoldTracker(0)
        t.update(100, true); assertEquals(100L, t.update(200, true).currentMs)
        assertEquals(0L, t.update(2000, true).currentMs)
        assertEquals(0L, t.update(1900, true).currentMs)
        t.reset(); assertEquals(0L, t.update(5000, true).currentMs)
        assertEquals(100L, t.update(5100, true).bestMs)
        t.reset(clearBest = true); assertEquals(0L, t.update(5200, true).bestMs)
    }
    @Test fun goalNeedsTenSecondsAfterSettling() {
        val t = HoldTracker()
        var state = HoldState(0, 0, false)
        for (now in 0L..10500L step 100) state = t.update(now, true)
        assertFalse(state.goalReached)
        assertTrue(t.update(10600, true).goalReached)
    }
}
