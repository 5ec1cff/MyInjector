package five.ec1cff.myinjector

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
        val handler = when (lpparam.packageName) {
            "tv.danmaku.bili" -> BilibiliXposedHandler()
            "com.fooview.android.fooview" -> FvXposedHandler()
            "com.lbe.security.miui" -> LbeHandler()
            "com.miui.securitycenter" -> MIUISecurityCenterHandler()
            "com.twitter.android" -> TwitterXposedHandler()
            "org.telegram.messenge" -> TelegramHandler()
            "com.termux" -> TermuxHandler()
            "com.tencent.wework" -> WeWorkXposedHandler()
            "com.android.systemui" -> SystemUIHandler()
            "com.tencent.mobileqq" -> QQXposedHandler()
            "com.zhihu.android" -> ZhihuXposedHandler()
            else -> return
        }
        handler.handleLoadPackage(lpparam)
    }
}