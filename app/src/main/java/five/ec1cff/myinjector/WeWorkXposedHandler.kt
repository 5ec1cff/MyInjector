package five.ec1cff.myinjector

import android.os.Process
import android.webkit.WebView
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.*
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.io.FileNotFoundException

class WeWorkXposedHandler: IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.tencent.wework" || lpparam.processName != "com.tencent.wework") return;
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-wework")
        Log.i("inject wework, pid=${Process.myPid()}, processName=${lpparam.processName}")

        fun enableDebug() {
            Log.d("tried to enable web debugging")
            WebView.setWebContentsDebuggingEnabled(true)
            try {
                loadClass("com.tencent.smtt.sdk.WebView").invokeStaticMethod(
                    "setWebContentsDebuggingEnabled",
                    args(true),
                    argTypes(java.lang.Boolean.TYPE)
                )
            } catch (t: Throwable) {
                Log.e(t, "maybe failed to enable webview debugging")
            }
        }

        var webviewClientName = "com.tencent.wework.common.web.JsWebActivity\$i0"
        try {
            webviewClientName = File("/data/local/tmp/wework-inject.txt").readText().trim()
            Log.d("read webview client className $webviewClientName")
        } catch (e: FileNotFoundException) {
            Log.d("use default $webviewClientName")
        }

        findMethod(webviewClientName) {
            name == "onPageFinished"
        }.hookAfter {
            runOnMainThread {
                val webView = it.args[0]
                webView.invokeMethod("evaluateJavascript",
                    args(
                        File("/data/local/tmp/wework-inject.js").readText(),
                        null),
                    argTypes(
                        String::class.java,
                        loadClass("com.tencent.smtt.sdk.ValueCallback")
                    )
                )
                Log.d("inject javascript code")
                enableDebug()
            }
        }
    }
}