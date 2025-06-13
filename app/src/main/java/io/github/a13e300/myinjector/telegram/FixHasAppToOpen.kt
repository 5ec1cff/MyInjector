package io.github.a13e300.myinjector.telegram

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.hook

// 修复重复打开链接的问题
class FixHasAppToOpen : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.fixHasAppToOpen

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        val inBrowserOpenUrl = ThreadLocal<Boolean>()
        val started = ThreadLocal<Boolean>()
        val browserClass = findClass("org.telegram.messenger.browser.Browser")
        val targetNethod =
            browserClass.declaredMethods.filter { it.name == "openUrl" }.maxBy { it.parameterCount }
        targetNethod.hook(
            cond = ::isEnabled,
            before = {
                inBrowserOpenUrl.set(true)
                started.set(false)
            },
            after = {
                inBrowserOpenUrl.set(false)
            }
        )

        Activity::class.java.hook(
            "startActivity", Intent::class.java, Bundle::class.java, cond = ::isEnabled,
            before = { param ->
                if (inBrowserOpenUrl.get() == true && started.get() == true) {
                    param.result = null
                    return@hook
                }
            },
            after = { param ->
                if (inBrowserOpenUrl.get() == true && param.throwable == null) {
                    started.set(true)
                }
            }
        )
        Activity::class.java.deoptimize("startActivity")
    }
}
