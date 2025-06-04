package io.github.a13e300.myinjector.arch

import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.logE

abstract class IHook {
    lateinit var classLoader: ClassLoader
        private set
    lateinit var loadPackageParam: XC_LoadPackage.LoadPackageParam
        private set

    open fun hook(param: XC_LoadPackage.LoadPackageParam) {
        loadPackageParam = param
        classLoader = param.classLoader
        try {
            onHook(param)
        } catch (t: Throwable) {
            logE("hook failed: ${this::class.simpleName}", t)
        }
    }

    fun subHook(hook: IHook) {
        hook.hook(loadPackageParam)
    }

    protected fun findClass(name: String): Class<*> = XposedHelpers.findClass(name, classLoader)

    protected fun findClassOrNull(name: String): Class<*>? =
        XposedHelpers.findClassIfExists(name, classLoader)

    protected abstract fun onHook(param: XC_LoadPackage.LoadPackageParam)
}