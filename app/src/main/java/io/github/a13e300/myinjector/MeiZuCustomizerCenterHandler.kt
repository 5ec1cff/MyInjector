package io.github.a13e300.myinjector

import android.os.Environment
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.bridge.Unhook
import java.io.File

class MeiZuCustomizerCenterHandler : IHook() {
    override fun onHook() {
        hookDir()
    }

    private lateinit var unhook: Unhook

    private fun hookDir() = runCatching {
        var meetSAA = false
        var meetConstants = false
        unhook = Environment::class.java.hookAllAfter("getExternalStorageDirectory") { param ->
            var meetSelf = false
            var meetTarget = false
            for (element in Throwable().stackTrace) {
                if (element.methodName == "getExternalStorageDirectory") {
                    meetSelf = true
                } else if (meetSelf) {
                    if (element.className == "sa.a") {
                        if (!meetSAA) {
                            meetSAA = true
                            meetTarget = true
                        }
                        break
                    } else if (element.className == "com.meizu.net.lockscreenlibrary.admin.constants.Constants") {
                        if (!meetConstants) {
                            meetConstants = true
                            meetTarget = true
                        }
                        break
                    }
                }
            }
            if (meetTarget) {
                param.result = File(param.result as File, "Documents")
                logD("replaced dir ${param.result}", Throwable())
                if (meetSAA && meetConstants) {
                    logD("beforeHookedMethod: all hooked, unhook self")
                    unhook.unhook()
                }
            }
        }.single()
    }.onFailure {
        logE("hookDir: ", it)
    }
}
