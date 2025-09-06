package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.setObj

// 禁止询问联系人权限
class ContactPermission : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.contactPermission

    override fun onHook() {
        findClass("org.telegram.ui.ContactsActivity").hookAllBefore(
            "onResume",
            cond = ::isEnabled
        ) { param ->
            param.thisObject.setObj("checkPermission", false)
        }
    }
}
