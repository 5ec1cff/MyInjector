package five.ec1cff.myinjector

import android.app.Activity
import android.content.Intent
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import java.io.File

class BaiduIMEHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "MyInjector-BaiduIMEHandler"
    }

    private lateinit var lpparam: XC_LoadPackage.LoadPackageParam
    private var cacheFile: File? = null
    private var showMethodName = ""
    private var showMethodClass = ""

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        lpparam = param
        hookSplash()
        hookContactSuggestion()
    }

    private fun hookSplash() = runCatching {
        val splash =
            XposedHelpers.findClass("com.baidu.input.ImeAppMainActivity", lpparam.classLoader)
        XposedBridge.hookAllMethods(
            splash,
            "onCreate", object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    val intent = activity.intent
                        ?: Intent().also { activity.intent = it }
                    intent.putExtra("is_no_ads", true)
                }
            }
        )
        XposedBridge.hookAllMethods(
            splash,
            "endAd", object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    val activity = param.thisObject as Activity
                    val intent = activity.intent ?: return
                    if (intent.hasExtra("ime.intent_keys.next_route_path")) {
                        Log.d(TAG, "afterHookedMethod: finish it")
                        activity.finish()
                    }
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "doHookSplash: ", it)
    }

    private fun hookContactSuggestion() = runCatching {
        findContactSuggestion()
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(
                showMethodClass, lpparam.classLoader
            ), showMethodName, XC_MethodReplacement.DO_NOTHING
        )
    }.onFailure {
        Log.e(TAG, "hookContactSuggestion: ", it)
    }

    private fun findContactSuggestion() {
        val f = File(lpparam.appInfo.dataDir, "dexkit.tmp")
        cacheFile = f
        if (f.isFile) {
            try {
                val lines = f.inputStream().use { f.readLines() }
                val apkPath = lines[0]
                if (apkPath == lpparam.appInfo.sourceDir) {
                    showMethodClass = lines[1]
                    showMethodName = lines[2]
                    Log.d(TAG, "prepare: use cached result")
                    return
                } else {
                    Log.d(TAG, "prepare: need invalidate cache!")
                    f.delete()
                }
            } catch (t: Throwable) {
                Log.e(TAG, "prepare: failed to read", t)
                f.delete()
            }
        }
        Log.d(TAG, "prepare: start deobf")
        System.loadLibrary("dexkit")
        val bridge = DexKitBridge.create(lpparam.classLoader, true)
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
        Log.d(TAG, "prepare: found method: $showMethod")
        showMethodClass = showMethod.className
        showMethodName = showMethod.methodName
        f.bufferedWriter().use {
            it.write("${lpparam.appInfo.sourceDir}\n")
            it.write("$showMethodClass\n")
            it.write("$showMethodName\n")
        }
    }
}
