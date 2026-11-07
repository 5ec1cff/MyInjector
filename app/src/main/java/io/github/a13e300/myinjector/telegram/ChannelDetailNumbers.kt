package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.hookBefore

class ChannelDetailNumbers : MyDynHook("channelDetailNumbers") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.channelDetailNumbers

    override fun onHook() {
        // 频道关注者显示详细人数 3k → 3000
        val format = telegramApiMethod(classLoader, "org.telegram.messenger.LocaleController", "formatShortNumber", "int", "int[]")
        val numberIndex = format.parameterTypes.indexOf(Integer.TYPE)
        format.hookBefore(cond = ::isEnabled) {
            val number = it.args[numberIndex] as Int
            val rounded = it.args[1 - numberIndex] as IntArray?
            if (rounded != null) {
                rounded[0] = number
            }
            it.result = number.toString()
        }
    }
}
