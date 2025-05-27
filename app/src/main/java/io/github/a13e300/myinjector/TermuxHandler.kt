package io.github.a13e300.myinjector

import android.app.Activity
import android.view.View
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllNop
import io.github.a13e300.myinjector.arch.hookBefore

class TermuxHandler : IHook() {
    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        if (param.packageName != "com.termux") return
        val mainActivity = findClass("com.termux.app.TermuxActivity")
        Activity::class.java.hookBefore("finish") { param ->
            if (param.thisObject.javaClass == mainActivity) {
                (param.thisObject as Activity).finishAndRemoveTask()
                param.result = null
            }
        }

        mainActivity.hookAllAfter("onCreate") { param ->
            param.thisObject.getObjAs<View>("mTerminalView").setAutofillHints(null)
        }
        // fix termux view infinite update loop
        // https://github.com/termux/termux-app/blob/2f40df91e54662190befe3b981595209944348e8/app/src/main/java/com/termux/app/terminal/TermuxActivityRootView.java#L120
        findClass("com.termux.app.terminal.TermuxActivityRootView").hookAllNop("onGlobalLayout")
    }
}
