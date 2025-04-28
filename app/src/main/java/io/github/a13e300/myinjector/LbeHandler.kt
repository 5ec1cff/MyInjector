package io.github.a13e300.myinjector

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook

class LbeHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.lbe.security.miui") return
        logD("handleLoadPackage")
        XposedBridge.hookAllMethods(
            findClass(
                "com.miui.privacy.autostart.AutoRevokePermissionManager"
            ),
            "lambda\$startScheduleASCheck\$1",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    logD("stop auto revoke")
                    param.result = null
                }
            }
        )
    }
}