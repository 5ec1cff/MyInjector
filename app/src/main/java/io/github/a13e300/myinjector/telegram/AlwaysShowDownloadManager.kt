package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.hookBefore

class AlwaysShowDownloadManager : MyDynHook("alwaysShowDownloadManager") {
    override fun isFeatureEnabled(): Boolean =
        TelegramHandler.settings.alwaysShowDownloadManager

    override fun onHook() {
        telegramApiMethod(classLoader, "org.telegram.messenger.DownloadController", "hasUnviewedDownloads")
            .hookBefore(cond = ::isEnabled) { param ->
            param.result = true
        }
    }
}
