package io.github.a13e300.myinjector.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import io.github.a13e300.myinjector.telegram.withProfileMusicBackground

/** 用真实 Android Paint/Canvas 验证背景像素与临时状态恢复，仅存在于 debug。 */
class ProfileMusicPaintTestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val bitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply { color = Color.WHITE }
        fun pixel(expanded: Float): Int {
            bitmap.eraseColor(Color.TRANSPARENT)
            withProfileMusicBackground(paint, expanded) { canvas.drawRect(0f, 0f, 8f, 8f, paint) }
            return bitmap.getPixel(4, 4)
        }
        check(pixel(0f) == Color.WHITE)
        check(pixel(0.79f) == Color.WHITE)
        check(pixel(0.8f) == 0x66000000)
        check(pixel(1f) == 0x66000000)
        check(paint.color == Color.WHITE)
        check(pixel(0f) == Color.WHITE) // 展开再收起，白色主题恢复。
        paint.color = Color.rgb(30, 50, 90)
        val dark = paint.color
        check(pixel(1f) == dark)
        paint.color = Color.WHITE // 切换主题后不使用旧的颜色缓存。
        val shader = LinearGradient(0f, 0f, 8f, 8f, Color.WHITE, Color.WHITE, Shader.TileMode.CLAMP)
        paint.shader = shader
        check(pixel(1f) == 0x66000000)
        check(paint.color == Color.WHITE && paint.shader === shader)
        val failure = IllegalStateException("测试绘制异常")
        try {
            withProfileMusicBackground(paint, 1f) { throw failure }
        } catch (actual: IllegalStateException) {
            check(actual === failure)
        }
        check(paint.color == Color.WHITE && paint.shader === shader)
        withProfileMusicBackground(paint, 1f) {
            withProfileMusicBackground(paint, 1f) { check(paint.color == 0x66000000) }
            check(paint.color == 0x66000000)
        }
        check(paint.color == Color.WHITE && paint.shader === shader)
        bitmap.recycle()
        val result = "PASS: 展开半透明黑底、收起恢复、深色背景、主题切换、Shader 与异常／嵌套绘制恢复"
        Log.i("ProfileMusicPaintTest", result)
        setContentView(TextView(this).apply { text = result })
    }
}
