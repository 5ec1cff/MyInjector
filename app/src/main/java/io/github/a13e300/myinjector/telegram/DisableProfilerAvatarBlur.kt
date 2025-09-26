package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookAllNopIf

class DisableProfilerAvatarBlur : DynHook() {

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableProfilerAvatarBlur

    override fun onHook() {
        findClass("org.telegram.ui.Components.ProfileGalleryBlurView")
            .hookAllNopIf("draw", ::isEnabled)
    }
}
