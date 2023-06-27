package five.ec1cff.myinjector

import android.app.Activity
import android.os.Process
import android.view.View
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

val TWITTER_BROKEN_ACTIVITIES = listOf(
    "com.twitter.app.main.MainActivity",
    "com.twitter.android.search.implementation.results.SearchActivity"
)

class TwitterXposedHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.twitter.android" || !lpparam.processName.startsWith("com.twitter.android")) return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-twitter")
        Log.i("inject twitter, pid=${Process.myPid()}, processName=${lpparam.processName}")
        findMethod("android.app.Activity", findSuper = true) {
            name == "onCreate"
        }.hookAfter {
            if (it.thisObject.javaClass.name in TWITTER_BROKEN_ACTIVITIES) {
                // 修复 bitwarden 在 twitter 的搜索页面中错误地显示自动填充的问题
                Log.i("set important for autofill")
                with (it.thisObject as Activity) {
                    findViewById<View>(android.R.id.content).importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }
            }
        }
    }
}
