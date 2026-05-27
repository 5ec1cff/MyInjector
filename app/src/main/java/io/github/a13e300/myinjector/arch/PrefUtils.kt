@file:Suppress("DEPRECATION")

package io.github.a13e300.myinjector.arch

import android.content.res.ColorStateList
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.preference.Preference
import android.preference.PreferenceCategory
import android.preference.PreferenceGroup
import android.preference.PreferenceManager
import android.preference.SwitchPreference
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.CompoundButton
import android.widget.FrameLayout
import android.widget.TextView
import io.github.a13e300.myinjector.ui.ModernSettingsPalette
import io.github.a13e300.myinjector.ui.ModernSwitchView
import io.github.a13e300.myinjector.ui.dp
import io.github.a13e300.myinjector.ui.setTextSizeDp
import java.util.WeakHashMap

fun setTextViewMultiLine(vg: ViewGroup) {
    for (i in 0 until vg.childCount) {
        val v = vg.getChildAt(i)
        if (v is TextView) {
            v.isSingleLine = false
        } else if (v is ViewGroup) {
            setTextViewMultiLine(v)
        }
    }
}

internal object ModernPreferenceStyleRegistry {
    private val rows = WeakHashMap<Preference, RowStyle>()

    fun clear() {
        rows.clear()
    }

    fun markCardRows(preferences: List<Preference>) {
        preferences.forEachIndexed { index, preference ->
            rows[preference] = RowStyle(
                first = index == 0,
                last = index == preferences.lastIndex,
            )
        }
    }

    fun styleFor(preference: Preference): RowStyle =
        rows[preference] ?: RowStyle(first = true, last = true)
}

internal data class RowStyle(
    val first: Boolean,
    val last: Boolean,
)

private val STYLE_TAG_KEY = 0x7F00_0001

private fun applyModernPreferenceStyle(
    view: View,
    isCategory: Boolean = false,
    rowStyle: RowStyle = RowStyle(first = true, last = true),
    styleRoot: Boolean = true,
) {
    val palette = ModernSettingsPalette.from(view.context)
    if (styleRoot) {
        view.isPressed = false
        val oldStyle = view.getTag(STYLE_TAG_KEY) as? RowStyle
        if (oldStyle != rowStyle || view.background !is RippleDrawable) {
            view.setTag(STYLE_TAG_KEY, rowStyle)
            view.background = if (isCategory) {
                null
            } else {
                PreferenceRowBackground(view.context, palette, rowStyle).withNativeRipple(
                    view.context,
                    palette,
                    rowStyle,
                )
            }
        }
        view.jumpDrawablesToCurrentState()
    }
    if (!isCategory && styleRoot) {
        view.minimumHeight = view.context.dp(58)
        view.setPadding(view.context.dp(22), view.paddingTop, view.context.dp(22), view.paddingBottom)
    } else if (isCategory && styleRoot) {
        view.setPadding(view.paddingLeft, view.context.dp(18), view.paddingRight, view.context.dp(12))
    }
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child is TextView) {
                child.isSingleLine = false
                child.includeFontPadding = true
                when {
                    isCategory -> {
                        child.setTextColor(if (child.isEnabled) palette.accent else palette.summary)
                        child.setTextSizeDp(13.2f)
                        child.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    child.id == android.R.id.title -> {
                        child.setTextColor(if (child.isEnabled) palette.title else palette.summary)
                        child.setTextSizeDp(14.3f)
                        child.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                    }

                    child.id == android.R.id.summary -> {
                        child.setTextColor(palette.summary)
                        child.setTextSizeDp(12.8f)
                        child.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
                    }
                }
                child.alpha = if (child.isEnabled) 1f else 0.55f
            } else {
                applyModernPreferenceStyle(child, isCategory, rowStyle, styleRoot = false)
            }
        }
    }
}

private class PreferenceRowBackground(
    context: Context,
    private val palette: ModernSettingsPalette,
    private val rowStyle: RowStyle,
) : Drawable() {
    private val radius = context.dp(28).toFloat()
    private val dividerInset = context.dp(22).toFloat()
    private val dividerHeight = context.dp(0.6f).toFloat()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.surface
        style = Paint.Style.FILL
    }
    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.divider
        style = Paint.Style.FILL
    }
    private val rect = RectF()
    private val radii: FloatArray = rowRadii(radius, rowStyle)

    override fun draw(canvas: Canvas) {
        rect.set(bounds)
        val path = android.graphics.Path().apply {
            addRoundRect(rect, radii, android.graphics.Path.Direction.CW)
        }
        canvas.drawPath(path, paint)
        if (!rowStyle.last) {
            canvas.drawRect(
                bounds.left + dividerInset,
                bounds.bottom - dividerHeight,
                bounds.right - dividerInset,
                bounds.bottom.toFloat(),
                dividerPaint,
            )
        }
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        dividerPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        paint.colorFilter = colorFilter
        dividerPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}

private fun PreferenceRowBackground.withNativeRipple(
    context: Context,
    palette: ModernSettingsPalette,
    rowStyle: RowStyle,
): RippleDrawable {
    val mask = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadii = rowRadii(context.dp(28).toFloat(), rowStyle)
        setColor(Color.WHITE)
    }
    return RippleDrawable(ColorStateList.valueOf(palette.ripple), this, mask)
}

private fun rowRadii(radius: Float, rowStyle: RowStyle): FloatArray =
    floatArrayOf(
        if (rowStyle.first) radius else 0f,
        if (rowStyle.first) radius else 0f,
        if (rowStyle.first) radius else 0f,
        if (rowStyle.first) radius else 0f,
        if (rowStyle.last) radius else 0f,
        if (rowStyle.last) radius else 0f,
        if (rowStyle.last) radius else 0f,
        if (rowStyle.last) radius else 0f,
    )

private fun ModernSwitchView.bindPreference(preference: SwitchPreferenceCompat) {
    val canAnimate = boundPreferenceKey == preference.key
    boundPreferenceKey = preference.key
    onCheckedChangeListener = null
    isEnabled = preference.isEnabled
    isFocusable = false
    isPressed = false
    alpha = if (preference.isEnabled) 1f else 0.55f
    if (!isAnimatingToward(preference.isChecked)) {
        if (canAnimate) {
            setChecked(preference.isChecked)
        } else {
            setCheckedImmediately(preference.isChecked)
        }
    }
    onCheckedChangeListener = { checked ->
        preference.updateFromModernSwitch(checked) {
            setCheckedImmediately(preference.isChecked)
        }
    }
}

private fun replaceLegacySwitches(root: ViewGroup, preference: SwitchPreferenceCompat): ModernSwitchView? {
    var modernSwitch: ModernSwitchView? = null
    var i = 0
    while (i < root.childCount) {
        val child = root.getChildAt(i)
        if (child is ModernSwitchView) {
            child.bindPreference(preference)
            modernSwitch = child
            i++
        } else if (child is CompoundButton) {
            val replacement = ModernSwitchView(root.context, ModernSettingsPalette.from(root.context)).apply {
                bindPreference(preference)
            }
            val lp = child.layoutParams ?: ViewGroup.LayoutParams(root.context.dp(54), root.context.dp(32))
            root.removeViewAt(i)
            root.addView(replacement, i, lp.applySwitchSize(root.context))
            modernSwitch = replacement
            i++
        } else if (child is ViewGroup) {
            if (child.id == android.R.id.widget_frame) {
                modernSwitch = normalizeSwitchWidgetFrame(child, preference) ?: modernSwitch
            } else {
                modernSwitch = replaceLegacySwitches(child, preference) ?: modernSwitch
            }
            i++
        } else {
            i++
        }
    }
    return modernSwitch
}

private fun normalizeSwitchWidgetFrame(
    frame: ViewGroup,
    preference: SwitchPreferenceCompat,
): ModernSwitchView? {
    var modernSwitch: ModernSwitchView? = null
    var i = 0
    while (i < frame.childCount) {
        val child = frame.getChildAt(i)
        if (child is ModernSwitchView && modernSwitch == null) {
            modernSwitch = child
            i++
        } else {
            frame.removeViewAt(i)
        }
    }
    if (modernSwitch == null) {
        modernSwitch = ModernSwitchView(frame.context, ModernSettingsPalette.from(frame.context))
        frame.addView(
            modernSwitch,
            frame.modernSwitchLayoutParams(),
        )
    }
    modernSwitch.bindPreference(preference)
    return modernSwitch
}

private fun ViewGroup.modernSwitchLayoutParams(): ViewGroup.LayoutParams =
    if (this is FrameLayout) {
        FrameLayout.LayoutParams(context.dp(54), context.dp(32))
    } else {
        ViewGroup.LayoutParams(context.dp(54), context.dp(32))
    }

private fun ViewGroup.LayoutParams.applySwitchSize(context: Context): ViewGroup.LayoutParams {
    width = context.dp(54)
    height = context.dp(32)
    return this
}

private object NotifyDeferrer {
    private var depth = 0
    private val suppressedNotifies = linkedSetOf<() -> Unit>()
    private val handler = Handler(Looper.getMainLooper())

    fun isActive(): Boolean = depth > 0

    fun enter() { depth++ }

    fun leave() {
        depth--
        if (depth == 0 && suppressedNotifies.isNotEmpty()) {
            val pending = suppressedNotifies.toList()
            suppressedNotifies.clear()
            handler.postDelayed(
                {
                    pending.forEach { it() }
                },
                220L,
            )
        }
    }

    fun markSuppressed(notify: () -> Unit) {
        if (depth > 0) suppressedNotifies.add(notify)
    }
}

class SwitchPreferenceCompat(context: Context) : SwitchPreference(context) {
    private var boundModernSwitch: ModernSwitchView? = null

    @Deprecated("Deprecated in Java")
    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {

    }

    @Deprecated("Deprecated in Java")
    override fun onBindView(view: View) {
        super.onBindView(view)
        val viewGroup = view as ViewGroup
        viewGroup.isClickable = false
        viewGroup.isFocusable = false
        setTextViewMultiLine(viewGroup)
        applyModernPreferenceStyle(viewGroup, rowStyle = ModernPreferenceStyleRegistry.styleFor(this))
        boundModernSwitch = replaceLegacySwitches(viewGroup, this)
    }

    override fun notifyChanged() {
        if (NotifyDeferrer.isActive()) {
            NotifyDeferrer.markSuppressed(::notifyChangedNow)
            return
        }
        notifyChangedNow()
    }

    private fun notifyChangedNow() {
        super.notifyChanged()
    }

    fun updateFromModernSwitch(checked: Boolean, rollback: () -> Unit) {
        NotifyDeferrer.enter()
        try {
            if (callChangeListener(checked)) {
                try {
                    val field = javaClass.superclass.superclass.getDeclaredField("mChecked")
                    field.isAccessible = true
                    field.setBoolean(this, checked)
                } catch (_: Exception) {
                    isChecked = checked
                }
            } else {
                rollback()
            }
        } finally {
            NotifyDeferrer.leave()
        }
    }

    override fun onClick() {
        boundModernSwitch?.performClick() ?: super.onClick()
    }
}

class PreferenceCompat(context: Context) : Preference(context) {
    @Deprecated("Deprecated in Java")
    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {

    }

    @Deprecated("Deprecated in Java")
    override fun onBindView(view: View) {
        super.onBindView(view)
        setTextViewMultiLine(view as ViewGroup)
        applyModernPreferenceStyle(view, rowStyle = ModernPreferenceStyleRegistry.styleFor(this))
    }

    override fun notifyChanged() {
        if (NotifyDeferrer.isActive()) {
            NotifyDeferrer.markSuppressed(::notifyChangedNow)
            return
        }
        notifyChangedNow()
    }

    private fun notifyChangedNow() {
        super.notifyChanged()
    }
}

class PreferenceCategoryCompat(context: Context) : PreferenceCategory(context) {
    @Deprecated("Deprecated in Java")
    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager?) {

    }

    @Deprecated("Deprecated in Java")
    override fun onBindView(view: View) {
        super.onBindView(view)
        applyModernPreferenceStyle(view, true)
    }
}


inline fun PreferenceGroup.switchPreference(
    title: String,
    key: String,
    summary: String? = null,
    config: SwitchPreference.() -> Unit = {}
) {
    addPreference(SwitchPreferenceCompat(context).also {
        it.title = title
        it.key = key
        it.summary = summary
        it.config()
    })
}

inline fun PreferenceGroup.preference(
    title: String,
    key: String,
    summary: String? = null,
    config: Preference.() -> Unit = {}
) {
    addPreference(PreferenceCompat(context).also {
        it.title = title
        it.key = key
        it.summary = summary
        it.config()
    })
}

inline fun PreferenceGroup.category(title: String, config: PreferenceCategory.() -> Unit) {
    addPreference(PreferenceCategoryCompat(context).also {
        it.title = title
        it.config()
    })
}
