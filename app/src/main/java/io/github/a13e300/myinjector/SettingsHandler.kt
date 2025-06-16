package io.github.a13e300.myinjector

import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAllBefore

class SettingsHandler : IHook() {
    override fun onHook() {
        hookNotDisableNotificationSwitches()
    }

    private fun hookNotDisableNotificationSwitches() = runCatching {
        val switchPrefClass = findClass("androidx.preference.CheckBoxPreference")
        findClass("androidx.preference.Preference").hookAllBefore("setEnabled") { param ->
            if (switchPrefClass.isInstance(param.thisObject)) {
                if (!(param.args[0] as Boolean) &&
                    Throwable().stackTrace.any {
                        it.className.contains("AppNotificationSettings") ||
                                it.className.contains("ChannelNotificationSettings")
                    }
                ) {
                    // logD("prevent from disabling ${param.thisObject}", Throwable())
                    param.result = null
                }
            }
        }
    }.onFailure {
        logE("hookNotDisableNotificationSwitches: ", it)
    }

}
