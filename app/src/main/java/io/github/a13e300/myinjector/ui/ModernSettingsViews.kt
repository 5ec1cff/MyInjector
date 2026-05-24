package io.github.a13e300.myinjector.ui

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewConfiguration
import android.view.animation.DecelerateInterpolator
import android.widget.Checkable
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.roundToInt

internal fun Context.dp(value: Int): Int = dp(value.toFloat())

internal fun Context.dp(value: Float): Int =
    (value * resources.displayMetrics.density + 0.5f).roundToInt()

internal fun TextView.setTextSizeDp(value: Float) {
    setTextSize(TypedValue.COMPLEX_UNIT_DIP, value)
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
            val mode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isNight = mode == Configuration.UI_MODE_NIGHT_YES
            return if (isNight) {
                ModernSettingsPalette(
                    isLight = false,
                    background = Color.rgb(36, 36, 36),
                    surface = Color.rgb(28, 28, 28),
                    surfaceOverlay = withAlpha(Color.rgb(48, 48, 48), 226),
                    title = Color.rgb(232, 232, 232),
                    summary = Color.rgb(168, 168, 168),
                    divider = Color.rgb(54, 54, 54),
                    accent = Color.rgb(126, 149, 184),
                    switchOff = Color.rgb(65, 65, 65),
                    button = Color.rgb(55, 55, 55),
                    ripple = withAlpha(Color.WHITE, 24),
                )
            } else {
                ModernSettingsPalette(
                    isLight = true,
                    background = Color.rgb(236, 240, 246),
                    surface = Color.WHITE,
                    surfaceOverlay = withAlpha(Color.rgb(244, 247, 251), 230),
                    title = Color.rgb(37, 41, 50),
                    summary = Color.rgb(124, 132, 146),
                    divider = Color.rgb(224, 228, 235),
                    accent = Color.rgb(126, 149, 184),
                    switchOff = Color.rgb(214, 221, 232),
                    button = Color.rgb(232, 237, 245),
                    ripple = withAlpha(Color.rgb(20, 35, 55), 22),
                )
            }
        }
    }
}

internal class ModernSettingsHeader(
    context: Context,
    private val palette: ModernSettingsPalette,
) : FrameLayout(context) {
    private val closeButton = ModernCloseButton(context, palette)
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.title
        textAlign = Paint.Align.CENTER
        textSize = context.dp(17).toFloat()
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private var title: CharSequence = ""
    private var topInset = 0

    init {
        clipToPadding = false
        setWillNotDraw(false)

        addView(closeButton)
    }

    fun setTitle(title: CharSequence) {
        this.title = title
        invalidate()
    }

    fun setOnCloseClickListener(listener: OnClickListener) {
        closeButton.setOnClickListener(listener)
    }

    fun setTopInset(inset: Int) {
        topInset = inset
        requestLayout()
    }

    fun setScrollProgress(progress: Float) {
        alpha = 0.92f + 0.08f * progress
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        val horizontal = context.dp(20)
        val buttonSize = context.dp(42)
        val buttonTop = topInset + context.dp(9)

        closeButton.layout(
            width - horizontal - buttonSize,
            buttonTop,
            width - horizontal,
            buttonTop + buttonSize,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (title.isEmpty()) return
        val centerY = topInset + context.dp(32)
        val metrics = titlePaint.fontMetrics
        val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(title.toString(), width / 2f, baseline, titlePaint)
    }
}

internal class ModernSettingsCard(
    context: Context,
    palette: ModernSettingsPalette,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        setPadding(0, context.dp(6), 0, context.dp(6))
        background = palette.cardBackground(context)
        elevation = 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            clipToOutline = true
        }
    }
}

internal class ModernSettingsRow(
    context: Context,
    private val palette: ModernSettingsPalette,
    title: CharSequence,
    summary: CharSequence?,
    trailing: View?,
    showDivider: Boolean,
) : LinearLayout(context) {
    init {
        orientation = VERTICAL
        isClickable = true
        isFocusable = true
        foreground = RippleDrawable(ColorStateList.valueOf(palette.ripple), null, null)

        val row = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(if (summary.isNullOrBlank()) 52 else 66)
            setPadding(context.dp(22), context.dp(6), context.dp(20), context.dp(6))
        }

        val textColumn = LinearLayout(context).apply {
            orientation = VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val titleView = TextView(context).apply {
            text = title
            setTextColor(palette.title)
            setTextSizeDp(14.3f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = true
        }
        textColumn.addView(
            titleView,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )

        if (!summary.isNullOrBlank()) {
            val summaryView = TextView(context).apply {
                text = summary
                setTextColor(palette.summary)
                setTextSizeDp(12.8f)
                includeFontPadding = true
                setLineSpacing(context.dp(0.5f).toFloat(), 1f)
            }
            textColumn.addView(
                summaryView,
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            )
        }

        row.addView(
            textColumn,
            LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        if (trailing != null) {
            val trailingLp = LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ).apply {
                marginStart = context.dp(16)
            }
            row.addView(trailing, trailingLp)
        }

        addView(
            row,
            LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        )

        if (showDivider) {
            addView(
                View(context).apply { setBackgroundColor(palette.divider) },
                LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(1f),
                ).apply {
                    marginStart = context.dp(22)
                    marginEnd = context.dp(22)
                }
            )
        }
    }
}

internal class ModernActionButton(
    context: Context,
    palette: ModernSettingsPalette,
    title: CharSequence,
) : TextView(context) {
    init {
        text = title
        gravity = Gravity.CENTER
        setTextSizeDp(14.0f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(palette.title)
        background = palette.buttonBackground(context)
        minHeight = context.dp(46)
        isClickable = true
        isFocusable = true
    }
}

internal class ModernChevronView(
    context: Context,
    private val palette: ModernSettingsPalette,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.summary
        strokeWidth = context.dp(2f).toFloat()
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(context.dp(24), context.dp(24))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * 0.46f
        val cy = height * 0.5f
        val offset = context.dp(6).toFloat()
        canvas.drawLine(cx, cy - offset, cx + offset, cy, paint)
        canvas.drawLine(cx + offset, cy, cx, cy + offset, paint)
    }
}

internal class ModernCloseButton(
    context: Context,
    private val palette: ModernSettingsPalette,
) : View(context) {
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.title
        strokeWidth = context.dp(2f).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    init {
        val backgroundColor = if (palette.isLight) palette.surface else palette.button
        background = ripple(rounded(backgroundColor, context.dp(21)), palette.ripple)
        elevation = context.dp(3).toFloat()
        isClickable = true
        isFocusable = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val half = context.dp(6.2f).toFloat()
        canvas.drawLine(centerX - half, centerY - half, centerX + half, centerY + half, iconPaint)
        canvas.drawLine(centerX + half, centerY - half, centerX - half, centerY + half, iconPaint)
    }
}

internal class ModernSwitchView @JvmOverloads constructor(
    context: Context,
    private val palette: ModernSettingsPalette,
    attrs: AttributeSet? = null,
) : View(context, attrs), Checkable {
    internal var boundPreferenceKey: String? = null
    var onCheckedChangeListener: ((Boolean) -> Unit)? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val trackRect = RectF()
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        setShadowLayer(context.dp(0.8f).toFloat(), 0f, context.dp(0.8f).toFloat(), withAlpha(Color.BLACK, 45))
    }
    private var checked = false
    private var progress = 0f
    private var animator: ValueAnimator? = null
    private var downX = 0f
    private var downY = 0f
    private var downProgress = 0f
    private var dragging = false
    private var cancelClick = false
    private var inClick = false

    init {
        isClickable = true
        isFocusable = true
        setLayerType(LAYER_TYPE_SOFTWARE, thumbPaint)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            resolveSize(context.dp(52), widthMeasureSpec),
            resolveSize(context.dp(30), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val padding = context.dp(2).toFloat()
        trackRect.set(padding, padding, width - padding, height - padding)
        trackPaint.color = blend(palette.switchOff, palette.accent, progress)
        canvas.drawRoundRect(trackRect, trackRect.height() / 2f, trackRect.height() / 2f, trackPaint)

        val thumbRadius = (height - context.dp(7)) / 2f
        val minCx = context.dp(3.5f).toFloat() + thumbRadius
        val maxCx = width - context.dp(3.5f).toFloat() - thumbRadius
        val cx = minCx + (maxCx - minCx) * progress
        canvas.drawCircle(cx, height / 2f, thumbRadius, thumbPaint)
    }

    override fun performClick(): Boolean {
        if (inClick) return true
        inClick = true
        try {
            super.performClick()
            toggle()
        } finally {
            inClick = false
        }
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                animator?.cancel()
                isPressed = true
                downX = event.x
                downY = event.y
                downProgress = progress
                dragging = false
                cancelClick = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.x - downX
                val dy = event.y - downY
                val absDx = abs(dx)
                val absDy = abs(dy)
                if (!dragging) {
                    if (absDy > touchSlop && absDy > absDx) {
                        cancelClick = true
                        isPressed = false
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                    if (absDx > touchSlop && absDx > absDy) {
                        dragging = true
                        cancelClick = false
                        parent?.requestDisallowInterceptTouchEvent(true)
                    }
                }
                if (dragging) {
                    progress = (downProgress + dx / switchTravel()).coerceIn(0f, 1f)
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isPressed = false
                if (dragging) {
                    val oldValue = checked
                    val newValue = progress >= 0.5f
                    setCheckedInternal(newValue, animate = true)
                    if (oldValue != newValue) {
                        onCheckedChangeListener?.invoke(newValue)
                    }
                } else if (!cancelClick) {
                    performClick()
                }
                dragging = false
                cancelClick = false
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                isPressed = false
                dragging = false
                cancelClick = false
                setCheckedInternal(checked, animate = true)
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    internal fun isAnimatingToward(targetChecked: Boolean): Boolean =
        checked == targetChecked && animator != null

    override fun isChecked(): Boolean = checked

    override fun setChecked(checked: Boolean) {
        setCheckedInternal(checked, animate = true)
    }

    fun setCheckedImmediately(checked: Boolean) {
        setCheckedInternal(checked, animate = false)
    }

    override fun toggle() {
        val newValue = !checked
        setCheckedInternal(newValue, animate = true)
        onCheckedChangeListener?.invoke(newValue)
    }

    private fun setCheckedInternal(newValue: Boolean, animate: Boolean) {
        if (checked == newValue && progress == if (newValue) 1f else 0f) return
        checked = newValue
        val target = if (newValue) 1f else 0f
        animator?.cancel()
        if (!animate) {
            progress = target
            invalidate()
            return
        }
        animator = ValueAnimator.ofFloat(progress, target).apply {
            duration = 180
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                progress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun switchTravel(): Float =
        (width - height).coerceAtLeast(context.dp(1)).toFloat()
}

private fun rounded(color: Int, radius: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

private fun ripple(content: GradientDrawable, rippleColor: Int): RippleDrawable =
    RippleDrawable(ColorStateList.valueOf(rippleColor), content, content.constantState?.newDrawable())

private fun blend(from: Int, to: Int, progress: Float): Int {
    val inverse = 1f - progress
    return Color.argb(
        (Color.alpha(from) * inverse + Color.alpha(to) * progress).roundToInt(),
        (Color.red(from) * inverse + Color.red(to) * progress).roundToInt(),
        (Color.green(from) * inverse + Color.green(to) * progress).roundToInt(),
        (Color.blue(from) * inverse + Color.blue(to) * progress).roundToInt(),
    )
}

private fun withAlpha(color: Int, alpha: Int): Int =
    Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
