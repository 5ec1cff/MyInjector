package io.github.a13e300.myinjector

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LbeHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "Demo"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.lbe.security.miui") return
        Log.d(TAG, "handleLoadPackage")
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(
                "com.miui.privacy.autostart.AutoRevokePermissionManager",
                lpparam.classLoader
            ),
            "lambda\$startScheduleASCheck\$1",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.d(TAG, "stop auto revoke")
                    param.result = null
                }
            }
        )
    }
}