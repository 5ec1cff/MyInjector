package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.hookBefore

class AlwaysShowDownloadManager : MyDynHook("alwaysShowDownloadManager") {
    override fun isFeatureEnabled(): Boolean =
        TelegramHandler.settings.alwaysShowDownloadManager

    override fun onHook() {
        // org.telegram.messenger.* and its members are kept by Telegram's ProGuard rules.
        findClass("org.telegram.messenger.DownloadController").hookBefore(
            "hasUnviewedDownloads",
            cond = ::isEnabled
        ) { param ->
            param.result = true
        }
    }
}
