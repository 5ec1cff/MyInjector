package io.github.a13e300.myinjector.arch

import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

abstract class IHook {
    lateinit var classLoader: ClassLoader
        private set

    fun hook(param: XC_LoadPackage.LoadPackageParam) {
        classLoader = param.classLoader
        onHook(param)
    }

    protected fun findClass(name: String): Class<*> = XposedHelpers.findClass(name, classLoader)

    protected fun findClassOrNull(name: String): Class<*>? =
        XposedHelpers.findClassIfExists(name, classLoader)

    protected abstract fun onHook(param: XC_LoadPackage.LoadPackageParam)
}