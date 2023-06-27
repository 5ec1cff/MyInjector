package five.ec1cff.myinjector

import android.app.Activity
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MIUISecurityCenterHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.miui.securitycenter") {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            findMethod("com.miui.permcenter.privacymanager.SpecialPermissionInterceptActivity", findSuper = true) {
                name == "onCreate"
            }.hookAfter {
                val inst = it.thisObject as Activity
                inst.setResult(-1)
                inst.finish()
            }
        }
    }
}