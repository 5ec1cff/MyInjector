package five.ec1cff.myinjector

import android.app.Activity
import android.content.Intent
import android.os.Process
import android.view.View
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class StartActivityXposedHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.baidu.tieba") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-start")
        Log.i("inject startactivity, pid=${Process.myPid()}, processName=${lpparam.processName}")
        findMethod("android.app.IActivityManager\$Stub\$Proxy", findSuper = true) {
            name == "startActivity"
        }.hookAfter {
            val intent = it.args[2] as? Intent
            Log.e("IActivityManager startActivity intent=$intent extras=${intent?.extras}", Throwable())
        }
        findMethod("android.app.IActivityTaskManager\$Stub\$Proxy", findSuper = true) {
            name == "startActivity"
        }.hookAfter {
            val intent = it.args[3] as? Intent
            Log.e("IActivityTaskManager startActivity intent=$intent extras=${intent?.extras}", Throwable())
        }
    }
}