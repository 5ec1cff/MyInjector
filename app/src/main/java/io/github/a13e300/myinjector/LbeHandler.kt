package io.github.a13e300.myinjector

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAllNop

class LbeHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.lbe.security.miui") return
        logD("handleLoadPackage")
        findClass("com.miui.privacy.autostart.AutoRevokePermissionManager")
            .hookAllNop("lambda\$startScheduleASCheck\$1")
    }
}