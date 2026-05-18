package io.github.a13e300.myinjector

import android.content.res.Resources
import io.github.a13e300.myinjector.arch.newModuleResource
import io.github.a13e300.myinjector.bridge.LoadPackageParam
import io.github.a13e300.myinjector.system_server.SystemServerHookLoader
import io.github.a13e300.myinjector.telegram.TelegramHandler
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface

class Entry : XposedModule() {
    companion object {
        lateinit var modulePath: String
        val moduleRes: Resources by lazy {
            newModuleResource(modulePath)
        }
        lateinit var instance: Entry
    }

    private lateinit var processName: String

    override fun onModuleLoaded(param: XposedModuleInterface.ModuleLoadedParam) {
        instance = this
        modulePath = moduleApplicationInfo.sourceDir
        processName = param.processName
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        logI("MyInjector: ${param.packageName} ${processName}")
        val handler = when (param.packageName) {
            "com.fooview.android.fooview" -> FvXposedHandler()
            "com.lbe.security.miui" -> LbeHandler()
            "com.miui.securitycenter" -> MIUISecurityCenterHandler()
            "com.twitter.android" -> TwitterXposedHandler()
            in listOf(
                "org.telegram.messenger",
                "org.telegram.messenger.web",
                "org.telegram.messenger.beta",
                "org.telegram.plus",
                "com.exteragram.messenger",
                "com.radolyn.ayugram",
                "uz.unnarsx.cherrygram",
                "xyz.nextalone.nagram",
                "nu.gpu.nagram",
                "com.xtaolabs.pagergram",
                "fork.risin42.nagramx",
            ) -> TelegramHandler
            "com.termux" -> TermuxHandler()
            "com.android.systemui" -> SystemUIHandler()
            "com.zhihu.android" -> ZhihuXposedHandler()
            "com.android.chrome", "com.kiwibrowser.browser" -> ChromeHandler
            "com.baidu.input" -> BaiduIMEHandler()
            "com.miui.home" -> MiuiHomeHandler()
            "com.android.settings" -> SettingsHandler()
            "app.landrop.landrop_flutter" -> LanDropHandler()
            "com.android.intentresolver" -> IntentResolverHandler()
            "com.meizu.customizecenter" -> MeiZuCustomizerCenterHandler()
            "com.spotify.music" -> SpotifyHandler()
            "com.google.android.documentsui" -> DocumentsUIHandler()
            "com.easybrain.sudoku.android" -> SudokuHandler()
            "com.xingin.xhs" -> XhsHandler
            "com.miui.gallery" -> MiuiGalleryHandler()
            "tv.danmaku.bili" -> BiliHandler()
            else -> return
        }
        logPrefix = "[${handler.javaClass.simpleName}] "
        handler.hook(
            LoadPackageParam(
                param.packageName,
                processName,
                param.classLoader,
                param.applicationInfo,
                param.isFirstPackage
            )
        )
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        logPrefix = "[${SystemServerHookLoader.javaClass.simpleName}] "
        SystemServerHookLoader.hook(
            LoadPackageParam(
                "android",
                "android",
                param.classLoader,
                null,
                true
            )
        )
    }
}
