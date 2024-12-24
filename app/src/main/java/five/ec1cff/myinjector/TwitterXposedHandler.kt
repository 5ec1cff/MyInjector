package five.ec1cff.myinjector

import android.app.Activity
import android.os.Process
import android.util.Log
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

val TWITTER_BROKEN_ACTIVITIES = listOf(
    "com.twitter.app.main.MainActivity",
    "com.twitter.android.search.implementation.results.SearchActivity"
)

class TwitterXposedHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "myinjector-twitter"
    }
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.twitter.android" || !lpparam.processName.startsWith("com.twitter.android")) return
        Log.i(TAG, "inject twitter, pid=${Process.myPid()}, processName=${lpparam.processName}")
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("android.app.Activity", lpparam.classLoader),
            "onCreate",
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.thisObject.javaClass.name in TWITTER_BROKEN_ACTIVITIES) {
                        // 修复 bitwarden 在 twitter 的搜索页面中错误地显示自动填充的问题
                        Log.i(TAG, "set important for autofill")
                        with(param.thisObject as Activity) {
                            findViewById<View>(android.R.id.content).importantForAutofill =
                                View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                        }
                    }
                }
            }
        )
    }
}
