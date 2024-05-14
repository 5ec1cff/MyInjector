package five.ec1cff.myinjector

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
        /*
        XposedBridge.hookAllConstructors(
            XposedHelpers.findClass(
                "com.miui.permission.PermissionManager\$2", lpparam.classLoader
            ),
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    Log.e(TAG, "PermissionManager2 init", Throwable())
                }
            }
        )
        XposedHelpers.findAndHookMethod("com.lbe.security.service.provider.PermissionManagerProvider",
            lpparam.classLoader,
            "updatePackagePermission",
            String::class.java,
            Int::class.javaPrimitiveType,
            Long::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            Boolean::class.javaPrimitiveType,
            object : XC_MethodHook() {

                override fun afterHookedMethod(param: MethodHookParam) {
                    Log.e(TAG, "updatePackagePermission ${param.args[0]}", Throwable())
                }
            })*/
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