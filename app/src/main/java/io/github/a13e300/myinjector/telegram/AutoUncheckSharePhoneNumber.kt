package io.github.a13e300.myinjector.telegram

import android.view.View
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllAfter

// 添加联系人时自动取消勾选分享手机号码（原行为是默认勾选）
class AutoUncheckSharePhoneNumber : IHook() {
    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        findClass("org.telegram.ui.ContactAddActivity").hookAllAfter("createView") { param ->
            val checkBox = param.thisObject.getObj("checkBoxCell") as? View ?: return@hookAllAfter
            if (checkBox.call("isChecked") == true) {
                checkBox.performClick()
            }
        }
    }
}
