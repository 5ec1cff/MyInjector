package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.setObj

// 禁止询问联系人权限
class ContactPermission : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.contactPermission

    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        findClass("org.telegram.ui.ContactsActivity").hookAllBefore(
            "onResume",
            cond = ::isEnabled
        ) { param ->
            param.thisObject.setObj("checkPermission", false)
        }
    }
}
