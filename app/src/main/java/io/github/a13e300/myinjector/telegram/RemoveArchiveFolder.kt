package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllCBefore
import io.github.a13e300.myinjector.arch.setObj

class RemoveArchiveFolder : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.removeArchiveFolder
    override fun onHook(loadPackageParam: LoadPackageParam) {
        findClass("org.telegram.messenger.MessagesController").hookAllBefore(
            "getDialogs",
            cond = ::isEnabled
        ) { param ->
            param.thisObject.call("removeFolder", 1)
        }
        val specialPackageNames = listOf(
            "com.exteragram.messenger",
            "com.radolyn.ayugram",
        )
        if (loadPackageParam.packageName in specialPackageNames) {
            findClass("com.exteragram.messenger.utils.ChatUtils").hookAllBefore(
                "hasArchivedChats",
                cond = ::isEnabled
            ) { param ->
                param.result = true
            }
            findClass("com.exteragram.messenger.ExteraConfig").hookAllCBefore(
                cond = ::isEnabled
            ) { param ->
                param.thisObject.setObj("archivedChats", true)
            }
        }
    }
}
