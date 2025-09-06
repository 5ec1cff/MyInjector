package io.github.a13e300.myinjector.arch

import io.github.a13e300.myinjector.bridge.LoadPackageParam
import io.github.a13e300.myinjector.logE

abstract class IHook {
    lateinit var classLoader: ClassLoader
    lateinit var loadPackageParam: LoadPackageParam
        private set

    open fun hook(param: LoadPackageParam, loader: ClassLoader = param.classLoader) {
        loadPackageParam = param
        classLoader = loader
        try {
            onHook()
        } catch (t: Throwable) {
            logE("hook failed: ${this.javaClass.simpleName}", t)
        }
    }

    fun subHook(hook: IHook) {
        hook.hook(loadPackageParam, classLoader)
    }

    protected fun findClass(name: String): Class<*> = classLoader.findClass(name)

    protected fun findClassOrNull(name: String): Class<*>? = classLoader.findClassN(name)

    protected abstract fun onHook()
}