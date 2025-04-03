package io.github.a13e300.myinjector

import android.util.Log
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class LanDropHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        val fileTypeEnumClass = XposedHelpers.findClass("b4.a\$a", lpparam.classLoader)
        val fileTypeFile = XposedHelpers.getStaticObjectField(fileTypeEnumClass, "h") // "FILE"
        val fileTypeText = XposedHelpers.getStaticObjectField(fileTypeEnumClass, "d") // "TEXT"
        // guessType
        // "image", "video", "text", "url"
        XposedHelpers.findAndHookMethod(
            XposedHelpers.findClass("b4.a", lpparam.classLoader),
            "c",
            String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    Log.d("MyInjector", "afterHookedMethod: ${param.result}")
                    if (param.result == fileTypeText) {
                        Log.d("MyInjector", "afterHookedMethod: replaces")
                        param.result = fileTypeFile
                    }
                }
            }
        )
    }
}