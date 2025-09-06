package io.github.a13e300.myinjector.telegram

import android.view.View
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllAfter

// 添加联系人时自动取消勾选分享手机号码（原行为是默认勾选）
class AutoUncheckSharePhoneNumber : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.autoUncheckSharePhoneNumber

    override fun onHook() {
        findClass("org.telegram.ui.ContactAddActivity").hookAllAfter(
            "createView",
            cond = ::isEnabled
        ) { param ->
            val checkBox = param.thisObject.getObj("checkBoxCell") as? View ?: return@hookAllAfter
            if (checkBox.call("isChecked") == true) {
                checkBox.performClick()
            }
        }
    }
}
