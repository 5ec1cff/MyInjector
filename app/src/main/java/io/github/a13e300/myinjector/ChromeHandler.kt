package io.github.a13e300.myinjector

import android.content.Context
import android.view.MotionEvent
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.findClass
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllNop
import io.github.a13e300.myinjector.arch.toObfsInfo

// https://source.chromium.org/chromium/chromium/src/+/main:chrome/android/java/src/org/chromium/chrome/browser/SwipeRefreshHandler.java;l=293;drc=1b4216b712e68f7dc2b4b13e9d8e0c69203278e6
class ChromeHandler : IHook() {
    companion object {
        private const val KEY_swipeRefreshHander_Pull = "swipeRefreshHander_Pull"
        private const val KEY_swipeRefreshHander_Release = "swipeRefreshHander_Release"
    }

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        hookNoDetectObscure()
        if (loadPackageParam.processName.contains(":")) return
        val name = loadPackageParam.appInfo.className
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
                hookSwipeRefresh(loader)
            }
        } else {
            hookSwipeRefresh(classLoader)
        }
    }

    private fun hookNoDetectObscure() = runCatching {
        MotionEvent::class.java.hookAllAfter("getFlags") { param ->
            param.result = (param.result as Int).and(
                MotionEvent.FLAG_WINDOW_IS_OBSCURED.or(
                    MotionEvent.FLAG_WINDOW_IS_PARTIALLY_OBSCURED
                ).inv()
            )
        }
    }.onFailure {
        logE("hookNoDetectObscure", it)
    }

    private fun hookSwipeRefresh(loader: ClassLoader) {
        val tbl = createObfsTable("chrome", 1, classLoader = loader) { bridge ->
            val pullMethod = bridge.findMethod {
                matcher {
                    usingStrings("SwipeRefreshHandler.pull")
                }
            }.single()
            val releaseMethod = pullMethod.declaredClass!!.findMethod {
                matcher {
                    usingStrings("SwipeRefreshHandler.release")
                }
            }.single()

            mutableMapOf(
                KEY_swipeRefreshHander_Pull to pullMethod.toObfsInfo(),
                KEY_swipeRefreshHander_Release to releaseMethod.toObfsInfo(),
            )
        }

        val pullMethod = tbl[KEY_swipeRefreshHander_Pull]!!
        val releaseMethod = tbl[KEY_swipeRefreshHander_Release]!!

        val clazz = loader.findClass(pullMethod.className)
        clazz.hookAllNop(pullMethod.memberName)
        clazz.hookAllNop(releaseMethod.memberName)
    }
}
