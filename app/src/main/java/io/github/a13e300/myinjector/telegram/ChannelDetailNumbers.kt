package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookBefore

class ChannelDetailNumbers : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.channelDetailNumbers

    override fun onHook() {
        // 频道关注者显示详细人数 3k → 3000
        findClass("org.telegram.messenger.LocaleController").declaredMethods.single {
            it.name == "formatShortNumber" && it.parameterTypes.size == 2
        }.hookBefore(cond = ::isEnabled) {
            val number = it.args[0] as Int
            val rounded = it.args[1] as IntArray?
            if (rounded != null) {
                rounded[0] = number
            }
            it.result = number.toString()
        }
    }
}