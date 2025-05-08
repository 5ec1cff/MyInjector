package io.github.a13e300.myinjector

import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.callbacks.XC_LoadPackage

class Entry : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        lateinit var modulePath: String
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        logD("handleLoadPackage: ${lpparam.packageName} ${lpparam.processName}")
        val handler = when (lpparam.packageName) {
            "com.fooview.android.fooview" -> FvXposedHandler()
            "com.lbe.security.miui" -> LbeHandler()
            "com.miui.securitycenter" -> MIUISecurityCenterHandler()
            "com.twitter.android" -> TwitterXposedHandler()
            "org.telegram.messenger", "org.telegram.messenger.web","org.telegram.messenger.beta" -> TelegramHandler()
            "com.termux" -> TermuxHandler()
            "com.android.systemui" -> SystemUIHandler()
            "com.zhihu.android" -> ZhihuXposedHandler()
            "com.android.chrome", "com.kiwibrowser.browser" -> ChromeHandler()
            "com.baidu.input" -> BaiduIMEHandler()
            "com.miui.home" -> MiuiHomeHandler()
            "android" -> {
                if (lpparam.processName == "android") SystemServerHandler()
                else return
            }
            "com.android.settings" -> SettingsHandler()
            "app.landrop.landrop_flutter" -> LanDropHandler()
            "com.android.intentresolver" -> IntentResolverHandler()
            else -> return
        }
        logPrefix = "[${handler::class.java.simpleName}] "
        handler.hook(lpparam)
    }
}
