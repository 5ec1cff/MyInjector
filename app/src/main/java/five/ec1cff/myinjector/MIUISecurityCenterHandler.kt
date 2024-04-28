package five.ec1cff.myinjector

import android.app.Activity
import android.provider.Settings
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MIUISecurityCenterHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName == "com.miui.securitycenter") {
            EzXHelperInit.initHandleLoadPackage(lpparam)
            findMethod("com.miui.permcenter.privacymanager.SpecialPermissionInterceptActivity", findSuper = true) {
                name == "onCreate"
            }.hookBefore {
                val inst = it.thisObject as Activity
                val permName = inst.intent.getStringExtra("permName")
                if (permName == "miui_open_debug") {
                    Settings.Global.putInt(inst.contentResolver, Settings.Global.ADB_ENABLED, 1)
                }
                inst.setResult(-1)
                inst.finish()
            }
        }
    }
}