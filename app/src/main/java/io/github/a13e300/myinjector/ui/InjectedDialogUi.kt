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
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.min

// onClick 保持为最后一个参数，兼容所有现有调用处的尾随 lambda。
data class ModernInjectedDialogAction(
    val title: CharSequence,
    val dismissAfterClick: Boolean = true,
    val emphasized: Boolean = false,
    val onClickWithResult: (() -> Boolean)? = null,
    val onClick: (() -> Unit)? = null,
)

/** ComponentDialog 为普通宿主 Activity 提供 Compose 所需的生命周期和保存状态 owner。 */
@OptIn(ExperimentalLayoutApi::class)
internal fun showInjectorComposeDialog(
    context: Context,
    title: CharSequence,
    actions: List<ModernInjectedDialogAction> = emptyList(),
    content: @Composable () -> Unit,
): Dialog {
    val dialog = ComponentDialog(context)
    val compose = ComposeView(context).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            InjectorTheme {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.heightIn(max = LocalConfiguration.current.screenHeightDp.dp * 0.88f)
                            .padding(24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(title.toString(), style = MaterialTheme.typography.headlineSmall)
                        Box(Modifier.fillMaxWidth().weight(1f, fill = false)) { content() }
                        if (actions.isNotEmpty()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp, androidx.compose.ui.Alignment.End),
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                actions.forEach { action ->
                                    val click = {
                                        val accepted = action.onClickWithResult?.invoke() ?: run {
                                            action.onClick?.invoke()
                                            true
                                        }
                                        if (accepted && action.dismissAfterClick) dialog.dismiss()
                                    }
                                    if (action.emphasized) Button(onClick = click) { Text(action.title.toString()) }
                                    else TextButton(onClick = click) { Text(action.title.toString()) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    dialog.setContentView(compose)
    dialog.window?.apply {
        setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        setDimAmount(0.4f)
        setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    }
    dialog.show()
    dialog.window?.setLayout(
        min(context.resources.displayMetrics.widthPixels - context.dp(32), context.dp(560)),
        WindowManager.LayoutParams.WRAP_CONTENT,
    )
    return dialog
}

/** 对规则编辑器、应用选择器等既有 View 使用官方 AndroidView 互操作。 */
internal fun showModernInjectedDialog(
    context: Context,
    title: CharSequence,
    content: View,
    actions: List<ModernInjectedDialogAction> = emptyList(),
): Dialog = showInjectorComposeDialog(context, title, actions) {
    AndroidView(
        factory = { content },
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
    )
}

internal fun showModernInjectedTextInputDialog(
    context: Context,
    title: CharSequence,
    initialText: CharSequence = "",
    hint: CharSequence? = null,
    onSubmit: (String) -> Unit,
) {
    var text by mutableStateOf(initialText.toString())
    lateinit var dialog: Dialog
    fun submit() { onSubmit(text); dialog.dismiss() }
    dialog = showInjectorComposeDialog(
        context, title,
        listOf(
            ModernInjectedDialogAction("取消"),
            ModernInjectedDialogAction("确定", emphasized = true) { onSubmit(text) },
        ),
    ) {
        val requester = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        OutlinedTextField(
            value = text, onValueChange = { text = it },
            modifier = Modifier.fillMaxWidth().focusRequester(requester),
            singleLine = true, placeholder = hint?.let { { Text(it.toString()) } },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
        )
        LaunchedEffect(Unit) { requester.requestFocus(); keyboard?.show() }
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
