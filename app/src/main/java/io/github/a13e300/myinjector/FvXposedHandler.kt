package io.github.a13e300.myinjector

import android.os.Process
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook

class FvXposedHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.fooview.android.fooview") return
        logI("inject fv, pid=${Process.myPid()}, processName=${lpparam.processName}")
        try {
            val c = XposedHelpers.findClass("dalvik.system.VMRuntime", lpparam.classLoader)
            XposedHelpers.callMethod(XposedHelpers.callStaticMethod(c, "getRuntime"), "setHiddenApiExemptions", arrayOf("L"));
            logD("success to bypass")
        } catch (t: Throwable) {
            logE("failed to bypass", t)
        }

        if (lpparam.processName.endsWith(":fv"))
            hookNoTipNotificationPerm()
    }

    // 阻止 FV 发现系统的悬浮窗通知时应激哈气
    // 懒得写 dexkit 了
    private fun hookNoTipNotificationPerm() = runCatching {
        val isTarget = ThreadLocal<Boolean>()
        // 自动返回桌面
        // 特征： android.intent.action.MAIN, android.intent.category.HOME, 调用 performGlobalAction(2)
        XposedBridge.hookAllMethods(
            findClass(
                "com.fooview.android.fooview.fvprocess.FooAccessibilityService"
            ),
            "O0",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (isTarget.get() == true) {
                        isTarget.set(false)
                        param.result = null
                    }
                }
            }
        )
        // 远程调用 FooViewService 显示授权通知权限的窗口
        // binder 接口 f1, f1$a, f1$a$a, 特征 com.fooview.android.fooview.IMainUIService
        // 实际逻辑 FVMainUIService$a$y0 implements Runnable 特征：资源 id
        // authorize_floating_window_permission_desc 0x7f0e00d6 打开通知栏权限
        // remove_float_displaying_notification 0x7f0e0021 去除通知栏上“正在其他应用上层显示”的通知
        XposedBridge.hookAllMethods(
            findClass(
                "com.fooview.android.fooview.fvprocess.FooViewService"
            ),
            "F3",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    isTarget.set(true)
                    return null
                }
            }
        )
    }.onFailure {
        logE("hookNoTipNotificationPerm: ", it)
    }
}
