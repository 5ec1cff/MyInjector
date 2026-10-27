package io.github.a13e300.myinjector

import android.os.Build
import android.view.Window
import android.view.WindowManager
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookBefore

/** 在设置进程的所有 Activity 和弹窗上允许显示叠加层。 */
class SettingsAllowOverlays : IHook() {
    override fun onHook() {
        // 覆盖直接修改 LayoutParams.privateFlags 和旧版 addSystemFlags 等入口。
        runCatching {
            val privateFlags = WindowManager.LayoutParams::class.java
                .getDeclaredField("privateFlags").apply { isAccessible = true }
            val viewRootClass = findClass("android.view.ViewRootImpl")
            for (method in arrayOf("setView", "setLayoutParams")) {
                viewRootClass.hookAllBefore(method) { param ->
                    val attributes = param.args.firstOrNull { it is WindowManager.LayoutParams }
                        as? WindowManager.LayoutParams ?: return@hookAllBefore
                    runCatching {
                        val flags = privateFlags.getInt(attributes)
                        if (flags and HIDE_NON_SYSTEM_OVERLAY_WINDOWS != 0) {
                            privateFlags.setInt(
                                attributes, flags and HIDE_NON_SYSTEM_OVERLAY_WINDOWS.inv(),
                            )
                        }
                    }.onFailure { logE("clear Settings hide-overlay flag failed", it) }
                }
            }
        }.onFailure { logE("hook Settings window overlay flags failed", it) }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                Window::class.java.hookBefore("setHideOverlayWindows", Boolean::class.javaPrimitiveType!!) {
                    it.args[0] = false
                }
            }.onFailure { logE("hook Settings setHideOverlayWindows failed", it) }
        }
    }

    companion object {
        // WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS
        private const val HIDE_NON_SYSTEM_OVERLAY_WINDOWS = 1 shl 19
    }
}
