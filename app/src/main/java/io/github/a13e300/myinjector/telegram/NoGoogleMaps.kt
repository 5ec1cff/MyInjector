package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.hookConstantIf

// 选择位置时不再询问是否安装 Google Maps
class NoGoogleMaps : MyDynHook("noGoogleMaps") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.noGoogleMaps

    override fun onHook() {
        telegramApiMethod(classLoader, "org.telegram.messenger.AndroidUtilities", "isMapsInstalled", "org.telegram.ui.ActionBar.BaseFragment")
            .hookConstantIf(true) { isEnabled() }
    }
}
