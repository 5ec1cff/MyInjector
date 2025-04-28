package io.github.a13e300.myinjector

import android.app.Activity
import android.view.View
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.getObjAs

class TermuxHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.termux") return
        val mainActivity = findClass("com.termux.app.TermuxActivity")
        XposedHelpers.findAndHookMethod(
            Activity::class.java,
            "finish",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    if (param.thisObject.javaClass == mainActivity) {
                        (param.thisObject as Activity).finishAndRemoveTask()
                    }
                    return null
                }

            }
        )

        XposedBridge.hookAllMethods(
            mainActivity,
            "onCreate",
            object : de.robv.android.xposed.XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    param.thisObject.getObjAs<View>("mTerminalView").setAutofillHints(null)
                }
            }
        )
        // fix termux view infinite update loop
        // https://github.com/termux/termux-app/blob/2f40df91e54662190befe3b981595209944348e8/app/src/main/java/com/termux/app/terminal/TermuxActivityRootView.java#L120
        XposedBridge.hookAllMethods(
            findClass("com.termux.app.terminal.TermuxActivityRootView"),
            "onGlobalLayout",
            XC_MethodReplacement.DO_NOTHING
        )
    }
}
