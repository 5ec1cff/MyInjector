package io.github.a13e300.myinjector

import android.content.Context
import android.content.pm.ApplicationInfo
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.findClass
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllNop
import org.luckypray.dexkit.DexKitBridge
import java.io.File

// https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/SwipeRefreshHandler.java;l=293;drc=1b4216b712e68f7dc2b4b13e9d8e0c69203278e6
class ChromeHandler : IHook() {
    private var cacheFile: File? = null
    private var swipeRefreshHandlerClassName = ""
    private var swipeRefreshHandlerMethodPull = ""
    private var swipeRefreshHandlerMethodRelease = ""
    private lateinit var appInfo: ApplicationInfo

    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        if (param.processName.contains(":")) return
        val name = param.appInfo.className
        appInfo = param.appInfo
        logD("app name $name")
        // ensure split apk (split-chrome) is loaded, see:
        // https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/base/SplitCompatAppComponentFactory.java;l=136?q=SplitCompatAppComponentFactory&ss=chromium
        // https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/base/SplitChromeApplication.java;l=33?q=SplitChromeApplication&ss=chromium
        // LoadedApk$SplitDependencyLoaderImpl https://cs.android.com/android/platform/superproject/+/android14-qpr3-release:frameworks/base/core/java/android/app/LoadedApk.java;l=639;drc=c72510de9db33033259d7e32afe3cbaac2266649
        // Context.createContextForSplit
        val appClz = findClass(name)
        val m = runCatching { appClz.getDeclaredMethod("onCreate") }.getOrNull()
        if (m != null) {
            m.hookAfter { param ->
                // real classloader will be available after onCreate
                val loader = (param.thisObject as Context).classLoader
                logD("afterHookedMethod: get classloader $loader")
                doHook(loader)
            }
        } else {
            doHook(classLoader)
        }
    }

    private fun doHook(loader: ClassLoader) {
        doFind(loader)

        val clazz = loader.findClass(swipeRefreshHandlerClassName)

        clazz.hookAllNop(swipeRefreshHandlerMethodPull)


        clazz.hookAllNop(swipeRefreshHandlerMethodRelease)
    }

    private fun doFind(loader: ClassLoader) {
        val f = File(appInfo.dataDir, "dexkit.tmp")
        cacheFile = f
        if (f.isFile) {
            try {
                val lines = f.inputStream().use { f.readLines() }
                val apkPath = lines[0]
                if (apkPath == appInfo.sourceDir) {
                    swipeRefreshHandlerClassName = lines[1]
                    swipeRefreshHandlerMethodPull = lines[2]
                    swipeRefreshHandlerMethodRelease = lines[3]
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
        val bridge = DexKitBridge.create(loader, true)
        val pullMethod = bridge.findMethod {
            matcher {
                usingStrings("SwipeRefreshHandler.pull")
            }
        }.single()
        logD("prepare: found method: $pullMethod")
        val releaseMethod = pullMethod.declaredClass!!.findMethod {
            matcher {
                usingStrings("SwipeRefreshHandler.release")
            }
        }.single()
        logD("prepare: found method: $releaseMethod")
        swipeRefreshHandlerClassName = pullMethod.className
        swipeRefreshHandlerMethodPull = pullMethod.methodName
        swipeRefreshHandlerMethodRelease = releaseMethod.methodName
        f.bufferedWriter().use {
            it.write("${appInfo.sourceDir}\n")
            it.write("$swipeRefreshHandlerClassName\n")
            it.write("$swipeRefreshHandlerMethodPull\n")
            it.write("$swipeRefreshHandlerMethodRelease\n")
        }
    }
}
