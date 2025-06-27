package io.github.a13e300.myinjector.telegram

import android.view.View
import android.widget.FrameLayout
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookBefore

class HideFloatFab : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.hideFloatFab
    override fun onHook() {
        val dialogsActivityClass = findClass("org.telegram.ui.DialogsActivity")
        val fabHashCodes = mutableSetOf<Int>()
        dialogsActivityClass.hookAllAfter("createView", cond = ::isEnabled) { param ->
            val fab1 = param.thisObject.getObj("floatingButtonContainer") as? FrameLayout
            val fab2 = param.thisObject.getObj("floatingButton2Container") as? FrameLayout
            listOfNotNull(fab1, fab2).forEach { fab ->
                fabHashCodes.add(System.identityHashCode(fab))
                fab.visibility = View.GONE
            }
        }
        findClass("android.view.View").hookBefore("setVisibility", Int::class.javaPrimitiveType!!) { param ->
            val view = param.thisObject as? View ?: return@hookBefore
            if (fabHashCodes.contains(System.identityHashCode(view)) && isEnabled()) {
                param.args[0] = View.GONE
            }
        }
    }
}
