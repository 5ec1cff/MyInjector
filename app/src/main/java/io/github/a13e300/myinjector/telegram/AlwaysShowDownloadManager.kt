package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookAllBefore

class AlwaysShowDownloadManager : DynHook() {
    override fun isFeatureEnabled(): Boolean =
        TelegramHandler.settings.alwaysShowDownloadManager

    override fun onHook() {
        findClass("org.telegram.messenger.DownloadController").hookAllBefore(
            "hasUnviewedDownloads",
            cond = ::isEnabled
        ) { param ->
            param.result = true
        }
    }
}
