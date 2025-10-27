package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.arch.hookAllBefore

// 添加联系人时自动取消勾选分享手机号码（原行为是默认勾选）
class AutoUncheckSharePhoneNumber : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.autoUncheckSharePhoneNumber

    override fun onHook() {
        findClass("org.telegram.ui.ContactAddActivity").hookAllBefore(
            "fillItems",
            cond = ::isEnabled
        ) { param ->
            val activity = param.thisObject
            runCatching {
                val checkShare = activity.getObj("checkShare") as? Boolean ?: return@hookAllBefore
                if (!checkShare) return@hookAllBefore
                val firstSet = activity.getObj("firstSet") as? Boolean ?: return@hookAllBefore
                if (firstSet) {
                    activity.setObj("checkShare", false)
                }
            }
        }
    }
}
