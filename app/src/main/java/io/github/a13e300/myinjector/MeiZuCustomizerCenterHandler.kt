package io.github.a13e300.myinjector

import android.os.Environment
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import java.io.File

class MeiZuCustomizerCenterHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        hookDir()
    }

    private lateinit var unhook: XC_MethodHook.Unhook

    private fun hookDir() = runCatching {
        unhook = XposedBridge.hookAllMethods(
            Environment::class.java,
            "getExternalStorageDirectory",
            object : XC_MethodHook() {
                var meetSAA = false
                var meetConstants = false

                override fun afterHookedMethod(param: MethodHookParam) {
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
                }
            }
        ).single()
    }.onFailure {
        logE("hookDir: ", it)
    }
}
