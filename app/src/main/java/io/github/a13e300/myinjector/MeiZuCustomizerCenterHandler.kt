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

    // 在 <clinit> 中以 getExternalStorageDirectory() 构造存储路径的常量类。
    // sa.a 为主题美化 11.x 及更早的混淆名；fb.b 为 12.x（12.5.0 实测反编译）中同一逻辑的新名。
    private val customizePathClasses = setOf("sa.a", "fb.b")
    private val lockscreenConstantsClass = "com.meizu.net.lockscreenlibrary.admin.constants.Constants"

    private fun hookDir() = runCatching {
        var metCustomizePaths = false
        var metLockscreenConstants = false
        unhook = Environment::class.java.hookAllAfter("getExternalStorageDirectory") { param ->
            var meetSelf = false
            var meetTarget = false
            for (element in Throwable().stackTrace) {
                if (element.methodName == "getExternalStorageDirectory") {
                    meetSelf = true
                } else if (meetSelf) {
                    if (element.className in customizePathClasses) {
                        if (!metCustomizePaths) {
                            metCustomizePaths = true
                            meetTarget = true
                        }
                        break
                    } else if (element.className == lockscreenConstantsClass) {
                        if (!metLockscreenConstants) {
                            metLockscreenConstants = true
                            meetTarget = true
                        }
                        break
                    }
                }
            }
            if (meetTarget) {
                param.result = File(param.result as File, "Documents")
                logD("replaced dir ${param.result}", Throwable())
                if (metCustomizePaths && metLockscreenConstants) {
                    logD("beforeHookedMethod: all hooked, unhook self")
                    unhook.unhook()
                }
            }
        }.single()
    }.onFailure {
        logE("hookDir: ", it)
    }
}
