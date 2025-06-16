package io.github.a13e300.myinjector

import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAllNop

class LbeHandler : IHook() {
    override fun onHook() {
        if (loadPackageParam.packageName != "com.lbe.security.miui") return
        findClass("com.miui.privacy.autostart.AutoRevokePermissionManager")
            .hookAllNop("lambda\$startScheduleASCheck\$1")
    }
}