package io.github.a13e300.myinjector.systemui

import org.junit.Assert.*
import org.junit.Test

class ShadeLoopGeometryTest {
    @Test fun staggeredNotificationRowsMirrorOffsetsWithoutChangingTheirSpacing() {
        for (nativeDirection in listOf(-1, 1)) {
            val mirrored = ShadeLoopGeometry.animationDirection(nativeDirection, true)
            val offsets = (0..3).map { (it * 200 + 200) * mirrored }
            assertEquals(if (nativeDirection == 1) listOf(-200, -400, -600, -800)
                else listOf(200, 400, 600, 800), offsets)
            val native = ShadeLoopGeometry.animationDirection(nativeDirection, false)
            assertEquals((0..3).map { (it * 200 + 200) * nativeDirection }, (0..3).map { (it * 200 + 200) * native })
        }
    }

    @Test fun controlRowsMirrorWithRtlAndKeepTheZeroOffsetRowStationary() {
        for (rtlSign in listOf(-1, 1)) {
            val native = ShadeLoopGeometry.animationDirection(1, false)
            val mirrored = ShadeLoopGeometry.animationDirection(1, true)
            assertEquals(listOf(0, 200, 400, 600).map { it * rtlSign },
                (0..3).map { it * 200 * native * rtlSign })
            assertEquals(listOf(0, -200, -400, -600).map { it * rtlSign },
                (0..3).map { it * 200 * mirrored * rtlSign })
        }
    }

    @Test fun consecutiveSameDirectionDragsFinishEveryPreviousLegBeforeStartingTheNext() {
        for (distance in listOf(1000f, -1000f)) {
            val original = ShadeLoopGeometry(distance, -200f, 200f)
            for (dx in listOf(-500f, 500f)) {
                for (initialControl in listOf(false, true)) {
                    var fromControl = initialControl
                    repeat(6) {
                        val current = if (original.isOutward(fromControl, dx, 0f, 20f)) original.mirrored() else original
                        val targetControl = !fromControl
                        // 上一段尚未结束时继续同方向划动：对其目标页而言已是越界方向。
                        assertTrue(current.shouldFinishBeforeNextDrag(true, false, targetControl, dx, 0f, 20f))
                        val next = if (original.isOutward(targetControl, dx, 0f, 20f)) original.mirrored() else original
                        val start = if (targetControl) 0f else 1f
                        assertEquals(0.5f, start + dx / next.distance, 0f)
                        fromControl = targetControl
                    }
                }
            }
        }
    }

    @Test fun reversingAnAnimationAndExistingDragsKeepNativeContinuation() {
        for (distance in listOf(1000f, -1000f)) {
            val geometry = ShadeLoopGeometry(distance, -200f, 200f)
            for (targetControl in listOf(false, true)) {
                val outward = if (targetControl) -distance / 2 else distance / 2
                assertFalse(geometry.shouldFinishBeforeNextDrag(true, false, targetControl, -outward, 0f, 20f))
                assertFalse(geometry.shouldFinishBeforeNextDrag(false, false, targetControl, outward, 0f, 20f))
                assertFalse(geometry.shouldFinishBeforeNextDrag(true, true, targetControl, outward, 0f, 20f))
                assertFalse(geometry.shouldFinishBeforeNextDrag(true, false, targetControl, outward, 800f, 20f))
                assertFalse(geometry.shouldFinishBeforeNextDrag(true, false, targetControl, outward, 0f, 600f))
            }
        }
    }

    @Test fun onlyOutwardGesturesAreMirroredInLtrAndRtl() {
        for (distance in listOf(1000f, -1000f)) {
            val geometry = ShadeLoopGeometry(distance, -200f, 200f)
            for (fromControl in listOf(false, true)) {
                val outward = if (fromControl) -distance / 2 else distance / 2
                assertTrue(geometry.isOutward(fromControl, outward, 10f, 20f))
                assertFalse(geometry.isOutward(fromControl, -outward, 10f, 20f))
                val start = if (fromControl) 0f else 1f
                val progress = start + outward / geometry.mirrored().distance
                assertEquals(0.5f, progress, 0f)
                // 同步翻转速度后，原生 RTL/LTR 松手规则仍指向另一页。
                val normalizedVelocity = -outward * 10
                val toControl = if (distance < 0f) normalizedVelocity > 0f else normalizedVelocity < 0f
                assertEquals(!fromControl, toControl)
            }
        }
    }

    @Test fun bothPanelTranslationsAndTriggerOffsetsAreMirroredAndRestorable() {
        val geometry = ShadeLoopGeometry(1200f, -350f, 450f)
        val mirrored = geometry.mirrored()
        for (p in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            for (trigger in listOf(0f, 0.5f, 1f)) {
                val notification = -(1 - p) * geometry.distance + trigger * geometry.notificationShift
                val control = p * geometry.distance + trigger * geometry.controlShift
                assertEquals(-notification, -(1 - p) * mirrored.distance + trigger * mirrored.notificationShift, 0f)
                assertEquals(-control, p * mirrored.distance + trigger * mirrored.controlShift, 0f)
            }
        }
        assertEquals(geometry, mirrored.mirrored())
    }

    @Test fun verticalAmbiguousAndInvalidGesturesKeepNativeHandling() {
        val geometry = ShadeLoopGeometry(1000f, -200f, 200f)
        assertFalse(geometry.isOutward(true, -100f, 200f, 20f))
        assertFalse(geometry.isOutward(true, -20f, 0f, 20f))
        assertFalse(geometry.isOutward(true, -100f, 0f, 120f)) // 模糊手势放大 slop。
        assertFalse(geometry.isOutward(true, Float.NaN, 0f, 20f))
        assertFalse(geometry.copy(distance = 0f).isOutward(true, -100f, 0f, 20f))
        assertFalse(geometry.copy(distance = Float.POSITIVE_INFINITY).isOutward(true, -100f, 0f, 20f))
        assertFalse(geometry.copy(notificationShift = Float.NaN).isOutward(true, -100f, 0f, 20f))
    }
}
