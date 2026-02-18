package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllCBefore
import io.github.a13e300.myinjector.arch.setObj

class RemoveArchiveFolder : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.removeArchiveFolder
    override fun onHook() {
        val guard = ThreadLocal<Boolean>()
        findClass("org.telegram.messenger.MessagesController").hookAllBefore(
            "getDialogs",
            cond = ::isEnabled
        ) { param ->
            // for com.exteragram.messenger
            if (guard.get() != true) {
                guard.set(true)
                param.thisObject.call("removeFolder", 1)
                guard.set(false)
            }
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
