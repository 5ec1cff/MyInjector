package io.github.a13e300.myinjector

import android.content.ClipData
import android.content.ClipboardManager
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.query.matchers.ClassMatcher

class ZhihuXposedHandler : IHook() {
    // Zhihu 10.2.0 (20214) sha256=0774c8c812232dd1d1c0a75e32f791f7171686a8c68ce280c6b3d9b82cdde5eb
    // localSourceClass
    // class f$r implements com.zhihu.android.app.feed.ui2.feed.a.a<FeedList>
    // localSourceField
    // io.reactivex.subjects.ReplaySubject b
    // localSourceMethod
    // public void a(com.zhihu.android.api.model.FeedList)
    // use strings: "FeedRepository" "use cache"

    companion object {
        private const val KEY_localSourceMethod = "localSourceMethod"
        private const val KEY_localSourceField = "localSourceField"
    }

    override fun onHook(loadPackageParam: LoadPackageParam) {
        if (loadPackageParam.packageName != "com.zhihu.android") return
        hookDisableFeedAutoRefresh()
        hookClipboard()
    }

    private fun hookDisableFeedAutoRefresh() =
        kotlin.runCatching {
            val tbl = createObfsTable("zhihu", 1) { bridge ->
                val method = bridge.findMethod {
                    searchPackages("com.zhihu.android.app.feed.ui2.feed")
                    matcher {
                        usingEqStrings("FeedRepository", "use cache")
                    }
                }.also {
                    it.forEach { m ->
                        logD("$m")
                    }

                }.single()
                logD("prepare: found method: $method")
                val field = method.declaredClass!!.findField {
                    matcher {
                        type(ClassMatcher().className("io.reactivex.subjects.ReplaySubject"))
                    }
                }.single()
                logD("prepare: found field: $field")

                mutableMapOf(
                    KEY_localSourceMethod to method.toObfsInfo(),
                    KEY_localSourceField to field.toObfsInfo(),
                )
            }

            val method = tbl[KEY_localSourceMethod]!!
            val field = tbl[KEY_localSourceField]!!
            findClass(method.className).hookAllAfter(method.memberName) { param ->
                val b = param.thisObject.getObj(field.memberName) // ReplaySubject
                b.call("onComplete") // call onComplete to prevent from update
            }
            logD("hookDisableAutoRefresh success")
        }.onFailure {
            logE("hookDisableAutoRefresh: failed", it)
        }

    private fun hookClipboard() = kotlin.runCatching {
        ClipboardManager::class.java.hookAllBefore("setPrimaryClip") { param ->
            val clip = param.args[0] as ClipData
            if (clip.getItemAt(0).text.contains("?utm_psn=")) param.result = null
        }
    }.onFailure {
        logE("hookClipboard: ", it)
    }
}
