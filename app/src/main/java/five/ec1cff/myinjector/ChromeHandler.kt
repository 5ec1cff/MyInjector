package five.ec1cff.myinjector

import android.content.Context
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import org.luckypray.dexkit.DexKitBridge
import java.io.File

// https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/SwipeRefreshHandler.java;l=293;drc=1b4216b712e68f7dc2b4b13e9d8e0c69203278e6
class ChromeHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "MyInjector-ChromeHandler"
    }

    private var cacheFile: File? = null
    private var swipeRefreshHandlerClassName = ""
    private var swipeRefreshHandlerMethodPull = ""
    private var swipeRefreshHandlerMethodRelease = ""

    private lateinit var lpparam: XC_LoadPackage.LoadPackageParam

    override fun handleLoadPackage(param: XC_LoadPackage.LoadPackageParam) {
        if (this::lpparam.isInitialized) return
        lpparam = param
        val name = lpparam.appInfo.className
        Log.d(TAG, "app name $name")
        // ensure split apk (split-chrome) is loaded, see:
        // https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/base/SplitCompatAppComponentFactory.java;l=136?q=SplitCompatAppComponentFactory&ss=chromium
        // https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/base/SplitChromeApplication.java;l=33?q=SplitChromeApplication&ss=chromium
        // LoadedApk$SplitDependencyLoaderImpl https://cs.android.com/android/platform/superproject/+/android14-qpr3-release:frameworks/base/core/java/android/app/LoadedApk.java;l=639;drc=c72510de9db33033259d7e32afe3cbaac2266649
        // Context.createContextForSplit
        val appClz = XposedHelpers.findClass(name, lpparam.classLoader)
        val m = runCatching { appClz.getDeclaredMethod("onCreate") }.getOrNull()
        if (m != null) {
            XposedBridge.hookMethod(
                m,
                object : XC_MethodHook() {
                    // real classloader will be available after onCreate
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val loader = (param.thisObject as Context).classLoader
                        Log.d(
                            TAG,
                            "afterHookedMethod: get classloader $loader"
                        )
                        doHook(loader)
                    }
                }
            )
        } else {
            doHook(lpparam.classLoader)
        }
    }

    private fun doHook(loader: ClassLoader) {
        doFind(loader)

        val clazz = XposedHelpers.findClass(swipeRefreshHandlerClassName, loader)

        XposedBridge.hookAllMethods(
            clazz, swipeRefreshHandlerMethodPull, XC_MethodReplacement.DO_NOTHING
        )

        XposedBridge.hookAllMethods(
            clazz, swipeRefreshHandlerMethodRelease, XC_MethodReplacement.DO_NOTHING
        )
    }

    private fun doFind(loader: ClassLoader) {
        val f = File(lpparam.appInfo.dataDir, "dexkit.tmp")
        cacheFile = f
        if (f.isFile) {
            try {
                val lines = f.inputStream().use { f.readLines() }
                val apkPath = lines[0]
                if (apkPath == lpparam.appInfo.sourceDir) {
                    swipeRefreshHandlerClassName = lines[1]
                    swipeRefreshHandlerMethodPull = lines[2]
                    swipeRefreshHandlerMethodRelease = lines[3]
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
        val bridge = DexKitBridge.create(loader, true)
        val pullMethod = bridge.findMethod {
            matcher {
                usingStrings("SwipeRefreshHandler.pull")
            }
        }.single()
        Log.d(TAG, "prepare: found method: $pullMethod")
        val releaseMethod = pullMethod.declaredClass!!.findMethod {
            matcher {
                usingStrings("SwipeRefreshHandler.release")
            }
        }.single()
        Log.d(TAG, "prepare: found method: $releaseMethod")
        swipeRefreshHandlerClassName = pullMethod.className
        swipeRefreshHandlerMethodPull = pullMethod.methodName
        swipeRefreshHandlerMethodRelease = releaseMethod.methodName
        f.bufferedWriter().use {
            it.write("${lpparam.appInfo.sourceDir}\n")
            it.write("$swipeRefreshHandlerClassName\n")
            it.write("$swipeRefreshHandlerMethodPull\n")
            it.write("$swipeRefreshHandlerMethodRelease\n")
        }
    }
}
