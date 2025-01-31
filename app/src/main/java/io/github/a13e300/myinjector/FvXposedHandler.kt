package io.github.a13e300.myinjector

import android.os.Process
import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class FvXposedHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "MyInjector-fooview"
    }
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.fooview.android.fooview") return
        Log.i(TAG, "inject fv, pid=${Process.myPid()}, processName=${lpparam.processName}")
        try {
            val c = XposedHelpers.findClass("dalvik.system.VMRuntime", lpparam.classLoader)
            XposedHelpers.callMethod(XposedHelpers.callStaticMethod(c, "getRuntime"), "setHiddenApiExemptions", arrayOf("L"));
            Log.d(TAG, "success to bypass")
        } catch (t: Throwable) {
            Log.e(TAG, "failed to bypass", t)
        }
    }
}