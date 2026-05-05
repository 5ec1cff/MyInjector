package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.setObj

// 添加联系人时自动取消勾选分享手机号码（原行为是默认勾选）
class AutoUncheckSharePhoneNumber : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.autoUncheckSharePhoneNumber

    override fun onHook() {
        findClass("org.telegram.ui.ContactAddActivity").hookAllBefore(
            "fillItems",
            cond = ::isEnabled
        ) { param ->
            param.thisObject.setObj("checkShare", false)
        }
    }
}
