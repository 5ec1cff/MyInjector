package io.github.a13e300.myinjector

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.os.Bundle
import android.widget.Toast
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookConstant
import io.github.a13e300.myinjector.arch.hookNop
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.bridge.Xposed
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.WeakHashMap

/** 保留广告不可用时的原生结束通知，避免主页、详情页一直等待开屏结束。 */
class XiaomiMarketHandler : IHook() {
    private val hooks = mutableListOf<Unhook>()
    private var failureToast: MarketHookFailureToast? = null

    override fun onHook() {
        if (loadPackageParam.processName != "com.xiaomi.market") return
        val manager = findClass("com.xiaomi.market.ui.splash.SplashManager")
        val canShow = Xposed.findMethodExact(manager, "canShowSplash", arrayOf(Activity::class.java))
        val cover = Xposed.findMethodExact(manager, "shownSplashCoverIfNeed",
            arrayOf(Activity::class.java, String::class.java))
        val details = findClass("com.xiaomi.market.ui.splash.DetailSplashAdManager")
        val environment = findClass("com.xiaomi.market.ui.splash.DetailSplashAdManager\$Env")
        val canRequest = Xposed.findMethodExact(details, "canRequestSplashAd", arrayOf(environment))
        check(Modifier.isStatic(canShow.modifiers) && Modifier.isStatic(canRequest.modifiers) &&
            canShow.returnType == Boolean::class.javaPrimitiveType &&
            canRequest.returnType == Boolean::class.javaPrimitiveType && cover.returnType == Void.TYPE)

        try {
            // 不直接跳过 tryAdSplash：任务会沿原生 false 分支调用 onSplashEnd 并通知页面继续。
            hooks += canShow.hookConstant(false)
            // 封面先于异步资格判断设置；同步阻止它，避免无广告也短暂显示全屏遮罩。
            hooks += cover.hookNop()
            // 外部进入详情页时，在置 isRequesting 及注册等待任务之前走原生无广告分支。
            hooks += canRequest.hookConstant(false)
        } catch (error: Throwable) {
            onUnhook()
            throw error
        }
        logD("market cold, foreground and detail splash ads disabled")
    }

    override fun onHookError(t: Throwable) {
        // 通知入口使用框架生命周期，不依赖已经定位失败的商店类。
        runCatching {
            failureToast = MarketHookFailureToast().also { it.install() }
        }.onFailure { logE("install market hook failure toast", it) }
    }

    override fun onUnhook(): Boolean {
        failureToast?.close()
        failureToast = null
        hooks.asReversed().forEach { it.unhook() }
        hooks.clear()
        return true
    }
}

/** 初始化可能发生在后台；等到页面真正恢复前台，再提示每次前台会话一次。 */
private class MarketHookFailureToast : Application.ActivityLifecycleCallbacks, AutoCloseable {
    private var bootstrap: Unhook? = null
    private var application: Application? = null
    private val started = Collections.newSetFromMap(WeakHashMap<Activity, Boolean>())
    private var pending = true
    @Volatile private var closed = false

    fun install() {
        bootstrap = Instrumentation::class.java.hookAfter("callActivityOnResume", Activity::class.java) { param ->
            if (closed || param.throwable != null) return@hookAfter
            val activity = param.args[0] as Activity
            if (activity.packageName != "com.xiaomi.market" || activity.isFinishing || activity.isDestroyed) {
                return@hookAfter
            }
            runCatching {
                if (application == null) {
                    activity.application.registerActivityLifecycleCallbacks(this)
                    application = activity.application
                }
                // 首次注册时当前页的 START/RESUME 已经分发，补记当前页。
                started.add(activity)
                onActivityResumed(activity)
                bootstrap?.unhook()
                bootstrap = null
            }.onFailure { logE("show market hook failure toast", it) }
        }
    }

    override fun onActivityStarted(activity: Activity) { if (!closed) started.add(activity) }

    override fun onActivityResumed(activity: Activity) {
        if (closed || !pending || activity.isFinishing || activity.isDestroyed) return
        runCatching {
            Toast.makeText(activity, "MyInjector：应用商店开屏广告 Hook 失败，请查看日志", Toast.LENGTH_LONG).show()
            pending = false
        }.onFailure { logE("show market hook failure toast", it) }
    }

    override fun onActivityStopped(activity: Activity) {
        if (closed) return
        started.remove(activity)
        if (started.isEmpty() && !activity.isChangingConfigurations) pending = true
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    override fun close() {
        closed = true
        bootstrap?.unhook()
        bootstrap = null
        application?.unregisterActivityLifecycleCallbacks(this)
        application = null
    }
}
