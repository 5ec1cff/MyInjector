package io.github.a13e300.myinjector

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class SettingsHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "MyInjector"
    }

    private lateinit var lpparam: XC_LoadPackage.LoadPackageParam

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        this.lpparam = lpparam
        hookNotDisableNotificationSwitches()
    }

    private fun hookNotDisableNotificationSwitches() = runCatching {
        val switchPrefClass = XposedHelpers.findClass("androidx.preference.CheckBoxPreference", lpparam.classLoader)
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("androidx.preference.Preference", lpparam.classLoader),
            "setEnabled",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (switchPrefClass.isInstance(param.thisObject)) {
                        if (!(param.args[0] as Boolean) &&
                            Throwable().stackTrace.any {
                                it.className.contains("AppNotificationSettings") ||
                                        it.className.contains("ChannelNotificationSettings")
                            }) {
                            // Log.d(TAG, "prevent from disabling ${param.thisObject}", Throwable())
                            param.result = null
                        }
                    }
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookNotDisableNotificationSwitches: ", it)
    }

}
