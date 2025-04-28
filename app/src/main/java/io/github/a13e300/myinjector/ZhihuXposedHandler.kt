package io.github.a13e300.myinjector

import android.content.ClipData
import android.content.ClipboardManager
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.ClassMatcher
import java.io.File

class ZhihuXposedHandler : IHook() {
    // Zhihu 10.2.0 (20214) sha256=0774c8c812232dd1d1c0a75e32f791f7171686a8c68ce280c6b3d9b82cdde5eb
    // class f$r implements com.zhihu.android.app.feed.ui2.feed.a.a<FeedList>
    // io.reactivex.subjects.ReplaySubject b
    // public void a(com.zhihu.android.api.model.FeedList)
    // use strings: "FeedRepository" "use cache"
    private var localSourceClass = "com.zhihu.android.app.feed.ui2.feed.a.f\$r"
    private var localSourceMethod = "a"
    private var localSourceField = "b"

    private var cacheFile: File? = null

    private fun prepare(lpparam: LoadPackageParam) = kotlin.runCatching {
        val f = File(lpparam.appInfo.dataDir, "dexkit.tmp")
        cacheFile = f
        if (f.isFile) {
            try {
                val lines = f.inputStream().use { f.readLines() }
                val apkPath = lines[0]
                if (apkPath == lpparam.appInfo.sourceDir) {
                    localSourceClass = lines[1]
                    localSourceMethod = lines[2]
                    localSourceField = lines[3]
                    logD("prepare: use cached result")
                    return@runCatching
                } else {
                    logD("prepare: need invalidate cache!")
                    f.delete()
                }
            } catch (t: Throwable) {
                logE("prepare: failed to read", t)
                f.delete()
            }
        }
        logD("prepare: start deobf")
        System.loadLibrary("dexkit")
        val bridge = DexKitBridge.create(lpparam.classLoader, true)
        val method = bridge.findMethod {
            searchPackages("com.zhihu.android.app.feed.ui2.feed")
            matcher {
                usingStrings("FeedRepository", "use cache")
            }
        }[0]
        logD("prepare: found method: $method")
        val field = method.declaredClass!!.findField {
            matcher {
                type(ClassMatcher().className("io.reactivex.subjects.ReplaySubject"))
            }
        }[0]
        logD("prepare: found field: $field")
        localSourceClass = method.className
        localSourceMethod = method.methodName
        localSourceField = field.fieldName
        f.bufferedWriter().use {
            it.write("${lpparam.appInfo.sourceDir}\n")
            it.write("$localSourceClass\n")
            it.write("$localSourceMethod\n")
            it.write("$localSourceField\n")
        }
    }.onFailure {
        logE("prepare: failed", it)
    }

    override fun onHook(lpparam: LoadPackageParam) {
        if (lpparam.packageName != "com.zhihu.android") return
        prepare(lpparam)
        hookDisableFeedAutoRefresh(lpparam)
        hookClipboard()
    }

    private fun hookDisableFeedAutoRefresh(lpparam: LoadPackageParam) =
        kotlin.runCatching {
            XposedBridge.hookAllMethods(
                findClass(localSourceClass),
                localSourceMethod,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val b = param.thisObject.getObj(localSourceField) // ReplaySubject
                        b.call("onComplete") // call onComplete to prevent from update
                    }
                }
            )
        }.onFailure {
            logE("hookDisableAutoRefresh: failed", it)
            cacheFile?.delete()
        }

    private fun hookClipboard() = kotlin.runCatching {
        XposedBridge.hookAllMethods(
            ClipboardManager::class.java,
            "setPrimaryClip",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val clip = param.args[0] as ClipData
                    if (clip.getItemAt(0).text.contains("?utm_psn=")) param.result = null
                }
            }
        )
    }.onFailure {
        logE("hookClipboard: ", it)
    }
}
