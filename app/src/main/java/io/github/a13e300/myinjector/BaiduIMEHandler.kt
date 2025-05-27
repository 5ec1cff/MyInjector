package io.github.a13e300.myinjector

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllNop
import org.luckypray.dexkit.DexKitBridge
import java.io.File

class BaiduIMEHandler : IHook() {
    private var cacheFile: File? = null
    private var showMethodName = ""
    private var showMethodClass = ""

    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        hookSplash()
        hookContactSuggestion(param.appInfo)
    }

    private fun hookSplash() = runCatching {
        val splash = findClass("com.baidu.input.ImeAppMainActivity")
        splash.hookAllBefore("onCreate") { param ->
            val activity = param.thisObject as Activity
            val intent = activity.intent
                ?: Intent().also { activity.intent = it }
            intent.putExtra("is_no_ads", true)
        }

        splash.hookAllAfter("endAd") { param ->
            val activity = param.thisObject as Activity
            val intent = activity.intent ?: return@hookAllAfter
            if (intent.hasExtra("ime.intent_keys.next_route_path")) {
                logD("afterHookedMethod: finish it")
                activity.finish()
            }
        }
    }.onFailure {
        logE("doHookSplash: ", it)
    }

    private fun hookContactSuggestion(appInfo: ApplicationInfo) = runCatching {
        findContactSuggestion(appInfo)
        findClass(showMethodClass).hookAllNop(showMethodName)
    }.onFailure {
        logE("hookContactSuggestion: ", it)
    }

    private fun findContactSuggestion(appInfo: ApplicationInfo) {
        val f = File(appInfo.dataDir, "dexkit.tmp")
        cacheFile = f
        if (f.isFile) {
            try {
                val lines = f.inputStream().use { f.readLines() }
                val apkPath = lines[0]
                if (apkPath == appInfo.sourceDir) {
                    showMethodClass = lines[1]
                    showMethodName = lines[2]
                    logD("prepare: use cached result")
                    return
                } else {
                    logD("prepare: need invalidate cache!")
                    f.delete()
                }
            } catch (t: Throwable) {
                logE("prepare: failed to read", t)
                f.delete()
            }
        }
        logD("prepare: start deobf")
        System.loadLibrary("dexkit")
        val bridge = DexKitBridge.create(classLoader, true)
        val showMethod = bridge.findClass {
            matcher {
                usingStrings("android.permission.READ_CONTACTS", "layout_inflater")
                addMethod {
                    addInvoke {
                        name = "getWindowToken"
                        declaredClass = "android.view.View"
                    }
                }
            }
        }.findMethod {
            matcher {
                addInvoke {
                    name = "getWindowToken"
                    declaredClass = "android.view.View"
                }
            }
        }.single()
        logD("prepare: found method: $showMethod")
        showMethodClass = showMethod.className
        showMethodName = showMethod.methodName
        f.bufferedWriter().use {
            it.write("${appInfo.sourceDir}\n")
            it.write("$showMethodClass\n")
            it.write("$showMethodName\n")
        }
    }
}
