package io.github.a13e300.myinjector.telegram

import android.view.View
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllAfter

class HideFloatFab : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.hideFloatFab
    override fun onHook() {
        findClass("org.telegram.ui.DialogsActivity").hookAllAfter(
            "createView",
            cond = ::isEnabled
        ) { param ->
            val smallFabContainer = param.thisObject.getObj("floatingButton2Container") as? View
            smallFabContainer?.visibility = View.GONE
            val largeFabContainer = param.thisObject.getObj("floatingButtonContainer") as? View
            largeFabContainer?.visibility = View.GONE
        }
    }
}
