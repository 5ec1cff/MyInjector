package io.github.a13e300.myinjector.telegram

import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.hookAllConstant

// 选择位置时不再询问是否安装 Google Maps
class NoGoogleMaps : IHook() {
    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        findClass("org.telegram.messenger.AndroidUtilities")
            .hookAllConstant("isMapsInstalled", true)
    }
}
