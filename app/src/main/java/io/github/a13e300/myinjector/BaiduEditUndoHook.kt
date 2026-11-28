package io.github.a13e300.myinjector

import android.content.res.ColorStateList
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.inputmethodservice.InputMethodService
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore

class BaiduEditUndoHook : IHook() {
    private val controlsTag = "myinjector.baidu.edit.undo"

    override fun onHook() {
        runCatching {
            val delegate = findClass(
                "com.baidu.input.ime.editor.popupdelegate.EditControlManagerDelegate"
            )
            val base = delegate.superclass!!
            // t() 重算原生按键位置，n() 绘制时才更新面板的纵向偏移 k。
            delegate.hookAllAfter("t") { param ->
                val owner = param.thisObject
                val popup = owner.getObjAs<RelativeLayout>("p")
                if (popup.findViewWithTag<View>(controlsTag) == null) {
                    val service = owner.getObjAs<InputMethodService>("b")
                    val controls = LinearLayout(popup.context).apply {
                        tag = controlsTag
                        orientation = LinearLayout.HORIZONTAL
                        visibility = View.GONE
                        addView(createButton(service, "撤销", android.R.id.undo))
                        addView(createButton(service, "重做", android.R.id.redo))
                    }
                    popup.addView(controls, RelativeLayout.LayoutParams(0, 0))
                }
            }
            delegate.hookAllAfter("n") { param ->
                updateControls(param.thisObject, base.getObjSAs("N"))
            }
            delegate.hookAllBefore("H") { param ->
                val popup = param.thisObject.getObjAs<RelativeLayout>("p")
                popup.findViewWithTag<View>(controlsTag)?.let(popup::removeView)
            }
        }.onFailure {
            logE("hookBaiduEditUndo: ", it)
        }
    }

    private fun createButton(service: InputMethodService, label: String, action: Int) =
        TextView(service).apply {
            text = label
            contentDescription = label
            gravity = Gravity.CENTER
            includeFontPadding = false
            isFocusable = false
            setOnClickListener {
                service.currentInputConnection?.performContextMenuAction(action)
            }
        }

    private fun updateControls(owner: Any, textColor: Int) {
        val popup = owner.getObjAs<RelativeLayout>("p")
        val controls = popup.findViewWithTag<LinearLayout>(controlsTag) ?: return
        val layout = owner.getObjAs<Int>("C0")
        // 只有圆形光标布局在行首、行尾按键之间留有空白。
        val start = owner.getObjAsN<Rect>("w0")
        val end = owner.getObjAsN<Rect>("x0")
        if ((layout != 5 && layout != 6) || start == null || end == null) {
            controls.visibility = View.GONE
            return
        }
        val gap = minOf(start.width(), end.width()) / 8
        val size = minOf(start.width(), end.width(), (end.left - start.right - 3 * gap) / 2)
        if (size <= 0) {
            controls.visibility = View.GONE
            return
        }
        val width = size * 2 + gap
        val left = (start.right + end.left - width) / 2
        val top = start.centerY() - size / 2 - owner.getObjAs<Int>("k")
        val params = controls.layoutParams as RelativeLayout.LayoutParams
        if (params.width != width || params.height != size ||
            params.leftMargin != left || params.topMargin != top
        ) {
            params.width = width
            params.height = size
            params.leftMargin = left
            params.topMargin = top
            controls.layoutParams = params
        }
        val outline = owner.getObjAs<Paint>("k0")
        // 绘制回调会反复执行，仅在尺寸或皮肤变化时更新子控件。
        val style = listOf(size, gap, textColor, outline.color, outline.strokeWidth.toBits())
        if (controls.getChildAt(0).tag != style) {
            for (index in 0..1) {
                (controls.getChildAt(index) as TextView).apply {
                    layoutParams = LinearLayout.LayoutParams(size, size).also {
                        if (index == 1) it.leftMargin = gap
                    }
                    setTextSize(TypedValue.COMPLEX_UNIT_PX, size * 0.38f)
                    setTextColor(textColor)
                    val border = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(android.graphics.Color.TRANSPARENT)
                        setStroke(outline.strokeWidth.toInt().coerceAtLeast(1), outline.color)
                    }
                    val mask = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(android.graphics.Color.WHITE)
                    }
                    background = RippleDrawable(
                        ColorStateList.valueOf((textColor and 0x00ffffff) or 0x33000000),
                        border,
                        mask
                    )
                }
            }
            controls.getChildAt(0).tag = style
        }
        controls.visibility = View.VISIBLE
    }
}
