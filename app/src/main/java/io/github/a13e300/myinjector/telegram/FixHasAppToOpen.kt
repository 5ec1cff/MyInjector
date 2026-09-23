package io.github.a13e300.myinjector.telegram

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.toObfsInfo

// 修复重复打开链接的问题
class FixHasAppToOpen : MyDynHook("fixHasAppToOpen") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.fixHasAppToOpen

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        creator.create("BrowserOpenUrl") { bridge ->
            bridge.findMethod {
                matcher {
                    usingEqStrings("autologin_token", "http", "https")
                }
            }.single().toObfsInfo()
        }
        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val BrowserOpenUrl = table["BrowserOpenUrl"]!!
        val inBrowserOpenUrl = ThreadLocal<Boolean>()
        val started = ThreadLocal<Boolean>()
        val browserClass = findClass(BrowserOpenUrl.className)
        val targetNethod =
            browserClass.declaredMethods.filter { it.name == BrowserOpenUrl.memberName }
                .maxBy { it.parameterCount }
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
