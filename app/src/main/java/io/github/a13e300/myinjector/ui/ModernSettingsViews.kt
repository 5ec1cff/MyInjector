package io.github.a13e300.myinjector.ui

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int = dp(value.toFloat())

internal fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).roundToInt()

internal fun TextView.setTextSizeDp(value: Float) {
    setTextSize(TypedValue.COMPLEX_UNIT_SP, value)
}

internal data class ModernSettingsPalette(
    val isLight: Boolean,
    val background: Int,
    val surface: Int,
    val surfaceOverlay: Int,
    val title: Int,
    val summary: Int,
    val divider: Int,
    val accent: Int,
    val switchOff: Int,
    val button: Int,
    val ripple: Int,
    val onAccent: Int,
) {
    fun cardBackground(context: Context): GradientDrawable =
        rounded(surface, context.dp(28))

    fun buttonBackground(context: Context): RippleDrawable =
        ripple(rounded(button, context.dp(22)), ripple)

    fun headerBackground(context: Context): GradientDrawable =
        rounded(surfaceOverlay, context.dp(32)).apply {
            setStroke(context.dp(1), withAlpha(if (isLight) Color.WHITE else Color.BLACK, 85))
        }

    fun headerScrim(): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                withAlpha(background, 246),
                withAlpha(background, 225),
                withAlpha(background, 120),
                Color.TRANSPARENT,
            )
        )

    companion object {
        fun from(context: Context): ModernSettingsPalette {
            val colors = injectorColorScheme(context)
            val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                Configuration.UI_MODE_NIGHT_YES
            return ModernSettingsPalette(
                isLight = !night,
                background = colors.surface.toArgb(),
                surface = colors.surfaceContainerLow.toArgb(),
                surfaceOverlay = colors.surfaceContainerHigh.toArgb(),
                title = colors.onSurface.toArgb(),
                summary = colors.onSurfaceVariant.toArgb(),
                divider = colors.outlineVariant.toArgb(),
                accent = colors.primary.toArgb(),
                switchOff = colors.surfaceContainerHighest.toArgb(),
                button = colors.secondaryContainer.toArgb(),
                ripple = colors.primary.copy(alpha = 0.12f).toArgb(),
                onAccent = colors.onPrimary.toArgb(),
            )
        }
    }
}

internal class ModernActionButton(
    context: Context,
    palette: ModernSettingsPalette,
    title: CharSequence,
    emphasized: Boolean = false,
) : TextView(context) {
    init {
        text = title
        gravity = Gravity.CENTER
        setTextSizeDp(14.0f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(if (emphasized) palette.onAccent else palette.title)
        background = if (emphasized) {
            ripple(rounded(palette.accent, context.dp(22)), palette.ripple)
        } else {
            palette.buttonBackground(context)
        }
        minHeight = context.dp(46)
        isClickable = true
        isFocusable = true
    }
}

private fun rounded(color: Int, radius: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

private fun ripple(content: GradientDrawable, rippleColor: Int): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(rippleColor), content, content.constantState?.newDrawable())

private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
