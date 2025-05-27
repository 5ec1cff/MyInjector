package io.github.a13e300.myinjector

import android.app.Activity
import android.os.Process
import android.view.View
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAllAfter

val TWITTER_BROKEN_ACTIVITIES = listOf(
    "com.twitter.app.main.MainActivity",
    "com.twitter.android.search.implementation.results.SearchActivity"
)

class TwitterXposedHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.twitter.android" || !lpparam.processName.startsWith("com.twitter.android")) return
        logI("inject twitter, pid=${Process.myPid()}, processName=${lpparam.processName}")
        findClass("android.app.Activity").hookAllAfter("onCreate") { param ->
            if (param.thisObject.javaClass.name in TWITTER_BROKEN_ACTIVITIES) {
                // 修复 bitwarden 在 twitter 的搜索页面中错误地显示自动填充的问题
                logI("set important for autofill")
                with(param.thisObject as Activity) {
                    findViewById<View>(android.R.id.content).importantForAutofill =
                        View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
                }
            }
        }
    }
}
