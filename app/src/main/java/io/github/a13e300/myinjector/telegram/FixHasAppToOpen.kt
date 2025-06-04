package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.hookAllConstantIf

// 修复重复打开链接的问题
// TODO: 目前没修好
class FixHasAppToOpen : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.fixHasAppToOpen

    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        // 这构式逻辑谁写的？
        // https://github.com/DrKLO/Telegram/blob/eee720ef5e48e1c434f4c5a83698dc4ada34aaa9/TMessagesProj/src/main/java/org/telegram/messenger/browser/Browser.java#L391
        findClass("org.telegram.messenger.browser.Browser")
            .hookAllConstantIf("hasAppToOpen", true) {
                isEnabled()
            }
    }
}
