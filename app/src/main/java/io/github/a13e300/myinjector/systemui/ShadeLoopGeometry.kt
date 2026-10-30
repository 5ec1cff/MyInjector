package io.github.a13e300.myinjector.systemui

import kotlin.math.abs

/** p=0 为控制中心，p=1 为通知；原生距离的符号已经包含 RTL。 */
internal data class ShadeLoopGeometry(val distance: Float, val notificationShift: Float, val controlShift: Float) {
    companion object {
        fun animationDirection(nativeDirection: Int, mirrored: Boolean): Int =
            if (mirrored) -nativeDirection else nativeDirection
    }

    fun mirrored() = ShadeLoopGeometry(-distance, -notificationShift, -controlShift)

    fun shouldFinishBeforeNextDrag(
        switching: Boolean, dragging: Boolean, targetControl: Boolean, dx: Float, dy: Float, slop: Float,
    ): Boolean = switching && !dragging && isOutward(targetControl, dx, dy, slop)

    fun isOutward(fromControl: Boolean, dx: Float, dy: Float, slop: Float): Boolean {
        if (!distance.isFinite() || distance == 0f || !notificationShift.isFinite() || !controlShift.isFinite() ||
            !dx.isFinite() || !dy.isFinite() ||
            !slop.isFinite() || slop < 0f || abs(dx) <= slop || abs(dx) <= abs(dy)) return false
        return if (fromControl) dx / distance < 0f else dx / distance > 0f
    }
}
