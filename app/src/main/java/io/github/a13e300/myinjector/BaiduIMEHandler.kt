package io.github.a13e300.myinjector

import android.app.Activity
import android.content.Intent
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllNop
import io.github.a13e300.myinjector.arch.toObfsInfo

class BaiduIMEHandler : IHook() {
    companion object {
        private const val KEY_showMethod = "showMethod"
    }

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        hookSplash()
        hookContactSuggestion()
    }

    private fun hookSplash() = runCatching {
        val splash = findClass("com.baidu.input.ImeAppMainActivity")
        splash.hookAllBefore("onCreate") { param ->
            val activity = param.thisObject as Activity
            val intent = activity.intent
                ?: Intent().also { activity.intent = it }
            intent.putExtra("is_no_ads", true)
        }

        splash.hookAllAfter("endAd") { param ->
            val activity = param.thisObject as Activity
            val intent = activity.intent ?: return@hookAllAfter
            if (intent.hasExtra("ime.intent_keys.next_route_path")) {
                activity.finish()
            }
        }
    }.onFailure {
        logE("doHookSplash: ", it)
    }

    private fun hookContactSuggestion() = runCatching {
        val tbl = createObfsTable("baiduime", 1) { bridge ->
            val showMethod = bridge.findClass {
                matcher {
                    usingStrings("android.permission.READ_CONTACTS", "layout_inflater")
                    addMethod {
                        addInvoke {
                            name = "getWindowToken"
                            declaredClass = "android.view.View"
                        }
                    }
                }
            }.findMethod {
                matcher {
                    addInvoke {
                        name = "getWindowToken"
                        declaredClass = "android.view.View"
                    }
                }
            }.single()

            mutableMapOf(KEY_showMethod to showMethod.toObfsInfo())
        }

        val showMethod = tbl[KEY_showMethod]!!

        findClass(showMethod.className).hookAllNop(showMethod.memberName)
    }.onFailure {
        logE("hookContactSuggestion: ", it)
    }
}
