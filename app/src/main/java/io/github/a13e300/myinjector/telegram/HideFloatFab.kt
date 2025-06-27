package io.github.a13e300.myinjector.telegram

import android.view.View
import android.widget.FrameLayout
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookBefore
import java.util.WeakHashMap

class HideFloatFab : DynHook() {
    private val fabViews = WeakHashMap<View, Boolean>()

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.hideFloatFab
    override fun onHook() {
        val dialogsActivityClass = findClass("org.telegram.ui.DialogsActivity")
        dialogsActivityClass.hookAllAfter("createView", cond = ::isEnabled) { param ->
            val fab1 = param.thisObject.getObj("floatingButtonContainer") as? FrameLayout
            val fab2 = param.thisObject.getObj("floatingButton2Container") as? FrameLayout
            listOfNotNull(fab1, fab2).forEach { fab ->
                fabViews[fab] = true
                fab.visibility = View.GONE
            }
        }
        findClass("android.view.View").hookBefore("setVisibility",
            Int::class.javaPrimitiveType!!,
            cond = ::isEnabled
        ) { param ->
            val view = param.thisObject as? View ?: return@hookBefore
            if (fabViews.containsKey(view)) {
                param.args[0] = View.GONE
            }
        }
    }
}
