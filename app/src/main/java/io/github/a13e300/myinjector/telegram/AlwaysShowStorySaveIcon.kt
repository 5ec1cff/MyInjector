package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam
import io.github.a13e300.myinjector.arch.hookAfter

class AlwaysShowStorySaveIcon : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.alwaysShowStorySaveIcon
    override fun onHook(loadPackageParam: LoadPackageParam) {
        findClass("org.telegram.ui.Stories.PeerStoriesView").hookAfter(
            "updatePosition",
            cond = ::isEnabled
        ) { param ->
            val peerStoriesViewInstance = param.thisObject
            XposedHelpers.setBooleanField(peerStoriesViewInstance, "allowShare", true)
            XposedHelpers.setBooleanField(peerStoriesViewInstance, "allowRepost", true)
            XposedHelpers.setBooleanField(peerStoriesViewInstance, "allowShareLink", true)
        }
    }
}
