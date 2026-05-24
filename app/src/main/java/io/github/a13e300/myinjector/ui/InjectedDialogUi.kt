package io.github.a13e300.myinjector.ui

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

data class ModernInjectedDialogAction(
    val title: CharSequence,
    val dismissAfterClick: Boolean = true,
    val onClick: (() -> Unit)? = null,
)

internal fun showModernInjectedDialog(
    context: Context,
    title: CharSequence,
    content: View,
    actions: List<ModernInjectedDialogAction> = emptyList(),
): Dialog {
    val palette = ModernSettingsPalette.from(context)
    val dialog = Dialog(context)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        setDimAmount(0.45f)
        setGravity(Gravity.CENTER)
        setWindowAnimations(0)
        attributes = attributes.apply {
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.WRAP_CONTENT
            gravity = Gravity.CENTER
            dimAmount = 0.45f
        }
    }

    val card = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = injectedRounded(palette.background, context.dp(30))
        setPadding(context.dp(24), context.dp(22), context.dp(24), context.dp(24))
    }

    card.addView(
        TextView(context).apply {
            text = title
            setTextColor(palette.title)
            setTextSizeDp(18f)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            includeFontPadding = true
        },
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            bottomMargin = context.dp(16)
        },
    )

    card.addView(
        content,
        LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ).apply {
            if (actions.isNotEmpty()) bottomMargin = context.dp(18)
        },
    )

    if (actions.isNotEmpty()) {
        val actionBar = LinearLayout(context).apply {
            orientation = if (actions.size <= 2) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        }
        actions.forEachIndexed { index, action ->
            val button = modernInjectedButton(context, palette, action.title).apply {
                setOnClickListener {
                    action.onClick?.invoke()
                    if (action.dismissAfterClick) dialog.dismiss()
                }
            }
            val lp = if (actions.size <= 2) {
                LinearLayout.LayoutParams(0, context.dp(46), 1f).apply {
                    if (index > 0) marginStart = context.dp(10)
                }
            } else {
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    context.dp(46),
                ).apply {
                    if (index < actions.lastIndex) bottomMargin = context.dp(8)
                }
            }
            actionBar.addView(button, lp)
        }
        card.addView(
            actionBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
    }

    val outer = FrameLayout(context).apply {
        minimumWidth = resources.displayMetrics.widthPixels
        setPadding(context.dp(24), 0, context.dp(24), 0)
        addView(
            card,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER,
            ),
        )
    }

    dialog.setContentView(
        outer,
        ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        ),
    )
    dialog.show()
    dialog.window?.setLayout(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
    )
    return dialog
}

internal fun showModernInjectedTextInputDialog(
    context: Context,
    title: CharSequence,
    initialText: CharSequence = "",
    hint: CharSequence? = null,
    onSubmit: (String) -> Unit,
) {
    val palette = ModernSettingsPalette.from(context)
    val input = EditText(context).apply {
        setText(initialText)
        setSelection(text.length)
        this.hint = hint
        setSingleLine(true)
        imeOptions = EditorInfo.IME_ACTION_DONE
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setTextColor(palette.title)
        setHintTextColor(palette.summary)
        setTextSizeDp(14f)
        background = modernInputBackground(context, palette, false)
        setPadding(context.dp(16), 0, context.dp(16), 0)
        setOnFocusChangeListener { v, hasFocus ->
            v.background = modernInputBackground(context, palette, hasFocus)
        }
    }
    val holder = FrameLayout(context).apply {
        addView(
            input,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                context.dp(48),
            ),
        )
    }
    val dialog = showModernInjectedDialog(
        context,
        title,
        holder,
        listOf(
            ModernInjectedDialogAction("取消"),
            ModernInjectedDialogAction("确定") {
                onSubmit(input.text.toString())
            },
        ),
    )
    input.post {
        input.requestFocus()
        context.getSystemService(InputMethodManager::class.java)?.showSoftInput(input, 0)
    }
    dialog.setOnDismissListener {
        context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(
            input.windowToken,
            0,
        )
    }
}

internal fun modernInjectedButton(
    context: Context,
    palette: ModernSettingsPalette,
    title: CharSequence,
): TextView =
    TextView(context).apply {
        text = title
        gravity = Gravity.CENTER
        setTextColor(palette.title)
        setTextSizeDp(14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        background = RippleDrawable(
            ColorStateList.valueOf(palette.ripple),
            injectedRounded(injectedControlColor(palette), context.dp(23)),
            null,
        )
        isClickable = true
        isFocusable = true
    }

internal fun modernInjectedMessageView(
    context: Context,
    text: CharSequence,
): TextView {
    val palette = ModernSettingsPalette.from(context)
    return TextView(context).apply {
        this.text = text
        setTextColor(palette.summary)
        setTextSizeDp(13.2f)
        includeFontPadding = true
        setLineSpacing(context.dp(2).toFloat(), 1f)
        background = injectedRounded(if (palette.isLight) Color.rgb(244, 247, 251) else palette.button, context.dp(18))
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
    }
}

internal fun modernInjectedScrollContent(
    context: Context,
    child: View,
    maxHeightFraction: Float = 0.58f,
): ScrollView =
    ScrollView(context).apply {
        isFillViewport = false
        isVerticalFadingEdgeEnabled = true
        setFadingEdgeLength(context.dp(18))
        clipToPadding = false
        setPadding(0, context.dp(4), 0, context.dp(4))
        addView(
            child,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            ),
        )
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            (resources.displayMetrics.heightPixels * maxHeightFraction).toInt(),
        )
    }

internal class ModernInjectedSearchBar(
    context: Context,
    private val palette: ModernSettingsPalette,
) : LinearLayout(context) {
    val editText: EditText
    private val inputShell: LinearLayout

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, context.dp(8), 0, context.dp(10))

        editText = EditText(context).apply {
            background = null
            hint = "搜索..."
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            maxLines = 1
            setSingleLine(true)
            setTextColor(palette.title)
            setHintTextColor(if (palette.isLight) Color.rgb(145, 153, 166) else Color.rgb(126, 126, 126))
            setTextSizeDp(16f)
            includeFontPadding = false
            setPadding(0, 0, 0, 0)
            setOnFocusChangeListener { _, hasFocus -> updateFocus(hasFocus) }
        }

        inputShell = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = searchBackground(false)
            setPadding(context.dp(18), 0, context.dp(18), 0)
            setOnClickListener {
                editText.requestFocus()
                context.getSystemService(InputMethodManager::class.java)?.showSoftInput(editText, 0)
            }
        }
        inputShell.addView(SearchIconView(context, palette), LayoutParams(context.dp(28), context.dp(28)))
        inputShell.addView(
            editText,
            LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                marginStart = context.dp(8)
            },
        )
        addView(inputShell, LayoutParams(LayoutParams.MATCH_PARENT, context.dp(48)))
    }

    private fun updateFocus(hasFocus: Boolean) {
        inputShell.background = searchBackground(hasFocus)
    }

    private fun searchBackground(focused: Boolean): GradientDrawable =
        injectedRounded(injectedControlColor(palette), context.dp(24)).apply {
            if (focused) {
                val strokeColor = if (palette.isLight) palette.accent else Color.rgb(105, 105, 105)
                setStroke(context.dp(2), strokeColor)
            }
        }
}

private class SearchIconView(
    context: Context,
    palette: ModernSettingsPalette,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.summary
        style = Paint.Style.STROKE
        strokeWidth = context.dp(2.2f).toFloat()
        strokeCap = Paint.Cap.ROUND
    }

    override fun onDraw(canvas: Canvas) {
        val r = width * 0.24f
        val cx = width * 0.42f
        val cy = height * 0.42f
        canvas.drawCircle(cx, cy, r, paint)
        canvas.drawLine(cx + r * 0.72f, cy + r * 0.72f, width * 0.74f, height * 0.74f, paint)
    }
}

private fun modernInputBackground(
    context: Context,
    palette: ModernSettingsPalette,
    focused: Boolean,
): GradientDrawable =
    injectedRounded(if (palette.isLight) Color.rgb(244, 247, 251) else palette.button, context.dp(18)).apply {
        if (focused) {
            val strokeColor = if (palette.isLight) palette.accent else Color.rgb(105, 105, 105)
            setStroke(context.dp(2), strokeColor)
        }
    }

private fun injectedRounded(color: Int, radius: Int): GradientDrawable =
    GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE
        cornerRadius = radius.toFloat()
        setColor(color)
    }

private fun injectedControlColor(palette: ModernSettingsPalette): Int =
    if (palette.isLight) Color.rgb(222, 229, 240) else Color.rgb(62, 62, 62)
