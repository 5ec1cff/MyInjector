package io.github.a13e300.myinjector

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook

class SettingsHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookNotDisableNotificationSwitches()
    }

    private fun hookNotDisableNotificationSwitches() = runCatching {
        val switchPrefClass = findClass("androidx.preference.CheckBoxPreference")
        XposedBridge.hookAllMethods(
            findClass("androidx.preference.Preference"),
            "setEnabled",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (switchPrefClass.isInstance(param.thisObject)) {
                        if (!(param.args[0] as Boolean) &&
                            Throwable().stackTrace.any {
                                it.className.contains("AppNotificationSettings") ||
                                        it.className.contains("ChannelNotificationSettings")
                            }) {
                            // logD("prevent from disabling ${param.thisObject}", Throwable())
                            param.result = null
                        }
                    }
                }
            }
        )
    }.onFailure {
        logE("hookNotDisableNotificationSwitches: ", it)
    }

}
