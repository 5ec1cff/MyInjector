package io.github.a13e300.myinjector.systemui

import kotlin.math.abs

/** 只识别从顶部、另一页热区开始的单指下拉；一旦判为横划或上划，本次不再接管。 */
internal class TopPullGesture private constructor(
    private val startX: Float,
    private val startY: Float,
    val toControl: Boolean,
) {
    enum class Decision { WAIT, REJECT, SWITCH }
    private var decision = Decision.WAIT

    fun move(x: Float, y: Float, slop: Float): Decision {
        if (decision != Decision.WAIT) return decision
        val dx = x - startX
        val dy = y - startY
        decision = when {
            !dx.isFinite() || !dy.isFinite() || !slop.isFinite() || slop < 0 -> Decision.REJECT
            dy < -slop || (abs(dx) > slop && abs(dx) >= abs(dy)) -> Decision.REJECT
            dy > slop && dy > abs(dx) -> Decision.SWITCH
            else -> Decision.WAIT
        }
        return decision
    }

    companion object {
        fun start(x: Float, y: Float, top: Float, width: Float,
                  controlStart: Float, controlEnd: Float, fromControl: Boolean): TopPullGesture? {
            if (listOf(x, y, top, width, controlStart, controlEnd).any { !it.isFinite() } ||
                top <= 0 || width <= 0 || controlStart < 0 || controlEnd > width ||
                controlStart >= controlEnd || x < 0 || x >= width || y < 0 || y >= top) return null
            val toControl = x > controlStart && x < controlEnd
            if (toControl == fromControl) return null
            return TopPullGesture(x, y, toControl)
        }
    }
}
