package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookAllConstantIf

// 选择位置时不再询问是否安装 Google Maps
class NoGoogleMaps : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.noGoogleMaps

    override fun onHook() {
        findClass("org.telegram.messenger.AndroidUtilities")
            .hookAllConstantIf("isMapsInstalled", true) { isEnabled() }
    }
}
