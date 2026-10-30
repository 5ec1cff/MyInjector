package io.github.a13e300.myinjector.systemui

import io.github.a13e300.myinjector.systemui.TopPullGesture.Decision.*
import org.junit.Assert.*
import org.junit.Test

class TopPullGestureTest {
    private fun start(x: Float, y: Float = 5f, fromControl: Boolean = false,
                      controlStart: Float = 500f, controlEnd: Float = 1000f) =
        TopPullGesture.start(x, y, 50f, 1000f, controlStart, controlEnd, fromControl)

    @Test fun switchesBothWaysOnlyFromTheOppositeTopHotZone() {
        val toControl = start(750f)!!
        assertTrue(toControl.toControl)
        assertEquals(WAIT, toControl.move(753f, 20f, 20f))
        assertEquals(SWITCH, toControl.move(753f, 50f, 20f))
        val toNotification = start(250f, fromControl = true)!!
        assertFalse(toNotification.toControl)
        assertEquals(SWITCH, toNotification.move(250f, 100f, 20f))
        assertNull(start(250f))
        assertNull(start(750f, fromControl = true))
        assertNull(start(750f, y = 50f))
        assertNull(start(750f, y = 300f))
    }

    @Test fun respectsNativeRtlAndTabletHotZones() {
        // Native RTL places the control center on the left.
        assertTrue(start(250f, controlStart = 0f, controlEnd = 500f)!!.toControl)
        assertFalse(start(750f, fromControl = true, controlStart = 0f, controlEnd = 500f)!!.toControl)
        // Native tablet/unfolded hot zone occupies one quarter instead of one half.
        assertNull(start(650f, controlStart = 750f))
        assertTrue(start(850f, controlStart = 750f)!!.toControl)
        assertFalse(start(650f, fromControl = true, controlStart = 750f)!!.toControl)
        assertTrue(start(150f, controlStart = 20f, controlEnd = 260f)!!.toControl)
    }

    @Test fun horizontalOrUpwardGestureCannotBecomeASwitchLater() {
        val horizontal = start(750f)!!
        assertEquals(REJECT, horizontal.move(790f, 10f, 20f))
        assertEquals(REJECT, horizontal.move(790f, 200f, 20f))
        val upward = start(750f)!!
        assertEquals(REJECT, upward.move(750f, -30f, 20f))
        assertEquals(REJECT, upward.move(750f, 200f, 20f))
        assertEquals(REJECT, start(750f)!!.move(800f, 55f, 20f))
    }

    @Test fun thresholdSupportsAmbiguousTouchesAndFastPullWithOnlyOneMove() {
        val gesture = start(750f)!!
        assertEquals(WAIT, gesture.move(750f, 35f, 40f))
        assertEquals(SWITCH, gesture.move(750f, 50f, 40f))
        assertEquals(SWITCH, start(750f)!!.move(760f, 500f, 20f))
        assertEquals(WAIT, start(750f)!!.move(750f, 25f, 20f))
    }

    @Test fun rejectsInvalidGeometryAndCoordinates() {
        assertNull(start(Float.NaN))
        assertNull(start(-1f))
        assertNull(start(1000f))
        assertNull(start(750f, y = -1f))
        assertNull(start(750f, controlStart = 800f, controlEnd = 700f))
        assertNull(TopPullGesture.start(10f, 0f, 0f, 1000f, 500f, 1000f, false))
        assertEquals(REJECT, start(750f)!!.move(Float.POSITIVE_INFINITY, 100f, 20f))
    }
}
