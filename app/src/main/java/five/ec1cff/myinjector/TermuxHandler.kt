package five.ec1cff.myinjector

import android.app.Activity
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class TermuxHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.termux") return
        val mainActivity =
            XposedHelpers.findClass("com.termux.app.TermuxActivity", lpparam.classLoader)
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
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    (XposedHelpers.getObjectField(
                        param.thisObject,
                        "mTerminalView"
                    ) as View).setAutofillHints(null)
                }
            }
        )
        // fix termux view infinite update loop
        // https://github.com/termux/termux-app/blob/2f40df91e54662190befe3b981595209944348e8/app/src/main/java/com/termux/app/terminal/TermuxActivityRootView.java#L120
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(
                "com.termux.app.terminal.TermuxActivityRootView",
                lpparam.classLoader
            ),
            "onGlobalLayout",
            XC_MethodReplacement.DO_NOTHING
        )
    }
}
