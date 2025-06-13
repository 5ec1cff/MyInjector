package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.hookConstantIf

// 不检查是否具有安装 apk 权限，直接打开（便于打开 apk，注意这不会真的让系统安装器认为 tg 有权限，主要是便于使用第三方安装器或其他工具打开 apk 文件）
class FakeInstallPermission : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.fakeInstallPermission

    override fun onHook(loadPackageParam: XC_LoadPackage.LoadPackageParam) {
        findClass("android.app.ApplicationPackageManager").hookConstantIf(
            "canRequestPackageInstalls",
            constant = true
        ) { isEnabled() }
    }
}
