package io.github.a13e300.myinjector

import android.os.Process
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.createPackageResources
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllReplace
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.result.MethodData

class FvXposedHandler : IHook() {
    companion object {
        private val KEY_CallsShowDialog = "CallsShowDialog"
        private val KEY_backToHome = "BackToHome"
    }

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        if (loadPackageParam.packageName != "com.fooview.android.fooview") return
        logI("inject fv, pid=${Process.myPid()}, processName=${loadPackageParam.processName}")
        try {
            findClass("dalvik.system.VMRuntime")
                .callS("getRuntime")
                .call("setHiddenApiExemptions", arrayOf("L"))
        } catch (t: Throwable) {
            logE("failed to bypass", t)
        }

        if (loadPackageParam.processName.endsWith(":fv"))
            hookNoTipNotificationPerm()
    }

    // 阻止 FV 发现系统的悬浮窗通知时弹出授权提示
    private fun hookNoTipNotificationPerm() = runCatching {
        val isTarget = ThreadLocal<Boolean>()

        val obfsTable = createObfsTable("fv", 1) { bridge ->

            // com.fooview.android.fooview.fvprocess.FooAccessibilityService#onAccessibilityEvent->G0 调用以下两个方法
            // 远程调用 FooViewService 显示授权通知权限的窗口
            // binder 接口 g1, g1$a, g1$a$a, 特征 com.fooview.android.fooview.IMainUIService
            // 实际逻辑 FVMainUIService$a$y0 implements Runnable 特征：资源 id
            // remove_float_displaying_notification 0x7f0e0021 去除通知栏上“正在其他应用上层显示”的通知
            // 此处 hook 同一进程的执行远程调用的方法
            // 这两个方法是先后调用的，而另一个方法在别滴地方也会调用，
            // 因此先检查是否调用了这个方法，然后设置 flag ，以便另一个方法确定是否是正确的上下文

            // 找 showDialog 的实现方法
            // 通过资源 id
            val appInfo = loadPackageParam.appInfo
            val res = createPackageResources(appInfo)
            val resId = res.getIdentifier(
                "remove_float_displaying_notification",
                "string",
                appInfo.packageName
            )
            if (resId == 0) error("no resource string:remove_float_displaying_notification found")

            // showDialog 是一个 runnable
            val showDialogImplMethod = bridge.findMethod {
                matcher {
                    usingNumbers(resId)
                    declaredClass {
                        addInterface(Runnable::class.java.name)
                    }
                }
            }.single()
            // 其他方法会调用 Runnable 的构造器
            val showDialogImplInitMethod =
                showDialogImplMethod.declaredClass!!.methods.single { it.name == "<init>" }

            // 找 IMainUIService 接口的类的 Stub 类
            val itfStubOnTransact = bridge.findMethod {
                matcher {
                    usingStrings("com.fooview.android.fooview.IMainUIService")
                    name("onTransact")
                    declaredClass {
                        superClass = "android.os.Binder"
                    }
                }
            }.single()
            val itfStubClz = itfStubOnTransact.declaredClass!!

            // 从 showDialog 构造器开始找 caller ，直到找到实现了 IMainUIService 的类为止
            var method: MethodData = showDialogImplInitMethod
            while (method.declaredClass?.superClass?.name != itfStubClz.name) {
                method = bridge.findMethod {
                    matcher {
                        addInvoke(method.descriptor)
                    }
                }.single()
            }
            val itfStubShowDialogMethod = method
            val itfClz = itfStubClz.interfaces[0]

            // 现在就知道 IMainUIService 的哪一个方法是 showDialog 了
            val itfShowDialogMethod =
                itfClz.methods.single { it.name == itfStubShowDialogMethod.name }

            // 继续找调用了 IMainUIService showDialog 的方法，直到碰到第一个参数是 AccessibilityEvent 的方法
            method = itfShowDialogMethod
            while (true) {
                val newMethod = bridge.findMethod {
                    matcher {
                        addInvoke(method.descriptor)
                    }
                }.single { it.name != "onTransact" }
                if (newMethod.paramTypeNames.getOrNull(0) == "android.view.accessibility.AccessibilityEvent") {
                    break
                }
                method = newMethod
            }
            // 这就是我们要 hook 的目标
            val methodCallsShowDialog = method

            // 自动返回桌面
            // 特征： android.intent.action.MAIN, android.intent.category.HOME, 调用 performGlobalAction(2)
            val methodBackToHome = bridge.findMethod {
                matcher {
                    usingStrings("android.intent.action.MAIN", "android.intent.category.HOME")
                    addInvoke("Landroid/accessibilityservice/AccessibilityService;->performGlobalAction(I)Z")
                }
            }.single()

            mutableMapOf(
                KEY_CallsShowDialog to methodCallsShowDialog.toObfsInfo(),
                KEY_backToHome to methodBackToHome.toObfsInfo(),
            )
        }

        val callsShowDialog = obfsTable[KEY_CallsShowDialog]!!
        val backToHome = obfsTable[KEY_backToHome]!!

        findClass(callsShowDialog.className)
            .hookAllReplace(callsShowDialog.memberName) { param ->
                isTarget.set(true)
                null
            }
        findClass(backToHome.className)
            .hookAllBefore(backToHome.memberName) { param ->
                if (isTarget.get() == true) {
                    isTarget.set(false)
                    param.result = null
                }
            }
    }.onFailure {
        logE("hookNoTipNotificationPerm: ", it)
    }
}
