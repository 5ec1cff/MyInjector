package io.github.a13e300.myinjector.telegram

import android.graphics.Color
import android.graphics.Paint

/** 展开后原生文字变白，仅在这一帧替换过亮的卡片背景。 */
internal inline fun <T> withProfileMusicBackground(paint: Paint, expanded: Float, draw: () -> T): T {
    if (expanded < 0.8f || Color.luminance(paint.color) < 0.85f) return draw()
    val color = paint.color
    val shader = paint.shader
    paint.shader = null
    paint.color = 0x66000000
    return try {
        draw()
    } finally {
        paint.color = color
        paint.shader = shader
    }
}
