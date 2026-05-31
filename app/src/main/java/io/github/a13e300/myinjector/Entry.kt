package io.github.a13e300.myinjector

import android.content.pm.ApplicationInfo
import android.content.res.Resources
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.newModuleResource
import io.github.a13e300.myinjector.bridge.LoadPackageParam
import io.github.a13e300.myinjector.system_server.SystemServerHandler
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

    private lateinit var savedInstance: Array<Any?>
    private var handler: IHook? = null

    private fun handlePackage(pkg: String, proc: String, cl: ClassLoader, info: ApplicationInfo?, isSystemServer: Boolean) {
        logI("MyInjector: $pkg $proc")
        val h = if (isSystemServer) {
            SystemServerHandler()
        } else {
            when (pkg) {
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
        }
        logPrefix = "[${h.javaClass.simpleName}] "
        savedInstance = arrayOf(pkg, proc, cl, info, isSystemServer)
        h.hook(
            LoadPackageParam(
                pkg, proc, cl, info, true
            )
        )
        handler = h
    }

    override fun onPackageReady(param: XposedModuleInterface.PackageReadyParam) {
        if (param.isFirstPackage) {
            handlePackage(
                param.packageName,
                processName,
                param.classLoader,
                param.applicationInfo,
                false,
            )
        }
    }

    override fun onSystemServerStarting(param: XposedModuleInterface.SystemServerStartingParam) {
        handlePackage("android", "android", param.classLoader, null, true)
    }

    override fun onHotReloading(param: XposedModuleInterface.HotReloadingParam): Boolean {
        logD("unhooking ...")
        if (handler?.onUnhook() != false) {
            param.setSavedInstanceState(savedInstance)
            logD("allowed to unload")
            return true
        }
        logD("reject to unload")
        return false
    }

    override fun onHotReloaded(param: XposedModuleInterface.HotReloadedParam) {
        instance = this
        super.onHotReloaded(param)
        logD("hotReloaded")
        val savedState = param.savedInstanceState as? Array<Any?>
        if (savedState == null || savedState.size != 5) {
            logE("invalid saved state: ${param.savedInstanceState}")
            return
        }
        logI("hotReloading ...")
        handlePackage(
            savedState[0] as String,
            savedState[1] as String,
            savedState[2] as ClassLoader,
            savedState[3] as ApplicationInfo?,
            savedState[4] as Boolean,
        )
    }
}
