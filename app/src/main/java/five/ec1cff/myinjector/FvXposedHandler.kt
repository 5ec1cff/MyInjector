package five.ec1cff.myinjector

import android.app.Activity
import android.os.Process
import android.view.View
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class FvXposedHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.fooview.android.fooview") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-fooview")
        Log.i("inject fv, pid=${Process.myPid()}, processName=${lpparam.processName}")
        try {
            val c = XposedHelpers.findClass("dalvik.system.VMRuntime", lpparam.classLoader)
            XposedHelpers.callMethod(XposedHelpers.callStaticMethod(c, "getRuntime"), "setHiddenApiExemptions", arrayOf("L"));
            Log.d("success to bypass")
        } catch (t: Throwable) {
            Log.e("failed to bypass", t)
        }
    }
}