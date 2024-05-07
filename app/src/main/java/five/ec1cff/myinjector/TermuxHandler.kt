package five.ec1cff.myinjector

import android.view.View
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.getObjectAs
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.hookBefore
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class TermuxHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.termux") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-termux")
        findMethod("com.termux.app.TermuxActivity") {
            name == "onCreate"
        }.hookAfter {
            it.thisObject.getObjectAs<View>("mTerminalView").setAutofillHints(null)
        }
        // fix termux view infinite update loop
        // https://github.com/termux/termux-app/blob/2f40df91e54662190befe3b981595209944348e8/app/src/main/java/com/termux/app/terminal/TermuxActivityRootView.java#L120
        findMethod("com.termux.app.terminal.TermuxActivityRootView") {
            name == "onGlobalLayout"
        }.hookBefore {
            it.setResult(null)
        }
    }
}
