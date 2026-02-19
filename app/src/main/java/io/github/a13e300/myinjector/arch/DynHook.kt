package io.github.a13e300.myinjector.arch

import io.github.a13e300.myinjector.bridge.LoadPackageParam
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.logI
import java.io.File
import java.io.InputStream
import java.io.OutputStream

abstract class DynHookManager<T : Any> : IHook() {
    open fun isEnabled(): Boolean = true
    private val hooks = mutableListOf<DynHook>()

    lateinit var settings: T
        protected set
    lateinit var settingFile: File
        protected set

    open fun onChanged() {
        hooks.forEach {
            subHook(it as IHook)
        }
    }

    fun subHook(hook: DynHook) {
        hooks.add(hook)
        hook.parent = this
        super.subHook(hook as IHook)
    }

    override fun onHook() {
        settingFile = File(loadPackageParam.appInfo.dataDir, "my_injector_settings")
        readSettings()
    }

    private fun readSettings() {
        settings = runCatching {
            if (settingFile.canRead()) {
                settingFile.inputStream().use {
                    onReadSettings(it)
                }
            } else {
                defaultSettings()
            }
        }.onFailure {
            logE("read settings failed", it)
            settingFile.delete()
        }.getOrDefault(defaultSettings())
        logI("current settings $settings")
    }

    fun updateSettings(newSettings: T) {
        settings = newSettings
        onChanged()
        runCatching {
            settingFile.outputStream().use {
                onWriteSettings(it, newSettings)
            }
        }.onFailure {
            logE("persist settings", it)
        }
    }

    abstract fun onReadSettings(input: InputStream): T
    abstract fun defaultSettings(): T
    abstract fun onWriteSettings(output: OutputStream, setting: T)
}

abstract class DynHook : IHook() {
    private var hooked = false
    lateinit var parent: DynHookManager<*>

    @Synchronized
    override fun hook(param: LoadPackageParam, loader: ClassLoader) {
        if (hooked) {
            // logD("already hooked ${this::class.simpleName}")
            return
        }
        if (!isEnabled()) {
            // logD("not enabled: ${this::class.java.simpleName}")
            return
        }
        // logD("hooking ${this::class.java.simpleName}")
        super.hook(param, loader)
        hooked = true
    }

    abstract fun isFeatureEnabled(): Boolean

    protected open fun isEnabled(): Boolean = parent.isEnabled() && isFeatureEnabled()
}
