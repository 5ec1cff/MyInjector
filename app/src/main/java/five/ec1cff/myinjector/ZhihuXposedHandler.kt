package five.ec1cff.myinjector

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class ZhihuXposedHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "MyInjector-zhihu"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.zhihu.android") return
        hookDisableFeedAutoRefresh(lpparam)
    }

    private fun hookDisableFeedAutoRefresh(lpparam: XC_LoadPackage.LoadPackageParam) =
        kotlin.runCatching {
            // TODO: use dexkit
            // Zhihu 10.2.0 (20214) sha256=0774c8c812232dd1d1c0a75e32f791f7171686a8c68ce280c6b3d9b82cdde5eb
            // class f$r implements com.zhihu.android.app.feed.ui2.feed.a.a<FeedList>
            // io.reactivex.subjects.ReplaySubject b
            // public void a(com.zhihu.android.api.model.FeedList)
            // use strings: "FeedRepository" "use cache"
            XposedBridge.hookAllMethods(
                XposedHelpers.findClass(
                    "com.zhihu.android.app.feed.ui2.feed.a.f\$r",
                    lpparam.classLoader
                ),
                "a",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val b = XposedHelpers.getObjectField(param.thisObject, "b") // ReplaySubject
                        XposedHelpers.callMethod(
                            b,
                            "onComplete"
                        ) // call onComplete to prevent from update
                    }
                }
            )
        }.onFailure {
            Log.d(TAG, "hookDisableAutoRefresh: failed", it)
        }
}
