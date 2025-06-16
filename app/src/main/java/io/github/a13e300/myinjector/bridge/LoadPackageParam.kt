package io.github.a13e300.myinjector.bridge

import android.content.pm.ApplicationInfo

class LoadPackageParam(param: de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam) {
    val packageName: String = param.packageName
    val processName: String = param.processName
    val classLoader: ClassLoader = param.classLoader
    val appInfo: ApplicationInfo = param.appInfo
    val isFirstApplication: Boolean = param.isFirstApplication
}
