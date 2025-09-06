package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.setObj

class AlwaysShowStorySaveIcon : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.alwaysShowStorySaveIcon
    override fun onHook() {
        findClass("org.telegram.ui.Stories.PeerStoriesView").hookAllAfter(
            "updatePosition",
            cond = ::isEnabled
        ) { param ->
            param.thisObject.setObj("allowShare", true)
            param.thisObject.setObj("allowRepost", true)
            param.thisObject.setObj("allowShareLink", true)
        }
    }
}
