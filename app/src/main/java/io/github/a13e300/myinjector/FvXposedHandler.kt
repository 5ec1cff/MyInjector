package io.github.a13e300.myinjector

import android.os.Process
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllReplace

class FvXposedHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.fooview.android.fooview") return
        logI("inject fv, pid=${Process.myPid()}, processName=${lpparam.processName}")
        try {
            findClass("dalvik.system.VMRuntime")
                .callS("getRuntime")
                .call("setHiddenApiExemptions", arrayOf("L"))
            logD("success to bypass")
        } catch (t: Throwable) {
            logE("failed to bypass", t)
        }

        if (lpparam.processName.endsWith(":fv"))
            hookNoTipNotificationPerm()
    }

    // 阻止 FV 发现系统的悬浮窗通知时应激哈气
    // 懒得写 dexkit 了
    // 当前仅适配 1.6.1
    private fun hookNoTipNotificationPerm() = runCatching {
        val isTarget = ThreadLocal<Boolean>()
        // com.fooview.android.fooview.fvprocess.FooAccessibilityService#onAccessibilityEvent->G0 调用以下两个方法
        // 远程调用 FooViewService 显示授权通知权限的窗口
        // binder 接口 g1, g1$a, g1$a$a, 特征 com.fooview.android.fooview.IMainUIService
        // 实际逻辑 FVMainUIService$a$y0 implements Runnable 特征：资源 id
        // remove_float_displaying_notification 0x7f0e0021 去除通知栏上“正在其他应用上层显示”的通知
        // 此处 hook 同一进程的执行远程调用的方法
        // 这两个方法是先后调用的，而另一个方法在别滴地方也会调用，
        // 因此先检查是否调用了这个方法，然后设置 flag ，以便另一个方法确定是否是正确的上下文
        findClass("com.fooview.android.fooview.fvprocess.FooViewService")
            .hookAllReplace("G3") { param ->
                isTarget.set(true)
                null
            }
        // 自动返回桌面
        // 特征： android.intent.action.MAIN, android.intent.category.HOME, 调用 performGlobalAction(2)
        findClass("com.fooview.android.fooview.fvprocess.FooAccessibilityService")
            .hookAllBefore("O0") { param ->
                if (isTarget.get() == true) {
                    isTarget.set(false)
                    param.result = null
                }
            }
    }.onFailure {
        logE("hookNoTipNotificationPerm: ", it)
    }
}
