package io.github.a13e300.myinjector

import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.getObjS

class LanDropHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        val fileTypeEnumClass = findClass("b4.a\$a")
        val fileTypeFile = fileTypeEnumClass.getObjS("h") // "FILE"
        val fileTypeText = fileTypeEnumClass.getObjS("d") // "TEXT"
        // guessType
        // "image", "video", "text", "url"
        XposedHelpers.findAndHookMethod(
            findClass("b4.a"),
            "c",
            String::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    logD("afterHookedMethod: ${param.result}")
                    if (param.result == fileTypeText) {
                        logD("afterHookedMethod: replaces")
                        param.result = fileTypeFile
                    }
                }
            }
        )
    }
}