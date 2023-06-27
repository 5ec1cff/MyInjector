package five.ec1cff.myinjector

import android.os.Process
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class QQXposedHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.tencent.mobileqq") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-qq")
        Log.i("inject qq, pid=${Process.myPid()}, processName=${lpparam.processName}")
        Runtime::class.java.getDeclaredMethod("exec", Array<String>::class.java).hookBefore {
            val args = it.args[0] as? Array<String>
            if (args != null && args[0] == "logcat") {
                args[0] = "LAGKAT"
                Log.d("fuck qq logcat")
            }
        }
    }
}