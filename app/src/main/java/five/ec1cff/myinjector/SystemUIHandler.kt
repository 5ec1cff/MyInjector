package five.ec1cff.myinjector

import android.app.AlertDialog
import android.app.AndroidAppHelper
import android.app.INotificationManager
import android.graphics.Color
import android.os.IBinder
import android.os.ServiceManager
import android.service.notification.StatusBarNotification
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.args
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.getObject
import com.github.kyuubiran.ezxhelper.utils.getObjectAs
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNull
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNullAs
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

class SystemUIHandler : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-systemui")
        var tv: TextView? = null
        val nm by lazy {
            INotificationManager.Stub.asInterface(ServiceManager.getService("notification"))
        }
        findMethod("com.android.systemui.statusbar.notification.modal.ModalController") {
            name == "enterModal"
        }.hookAfter {
            val sbn = it.thisObject.getObjectOrNull("entry")
                ?.getObjectOrNullAs<StatusBarNotification>("mSbn") ?: return@hookAfter
            if (tv == null) {
                val root = it.thisObject.getObjectAs<ViewGroup>("modalWindowView")
                val ctx = root.context
                val f = FrameLayout(ctx)
                // f.setBackgroundColor(Color.WHITE)
                tv = TextView(ctx)
                f.addView(tv)
                val popup = PopupWindow(
                    f,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                popup.showAtLocation(root, Gravity.NO_GRAVITY, 0, 0)
            }
            val channel = nm.getNotificationChannel(
                "com.android.systemui",
                sbn.getObject("user").invokeMethod("getIdentifier") as Int,
                sbn.packageName,
                sbn.notification.channelId
            )
            tv!!.text = "channel=${sbn.notification.channelId} (${channel.name})\npkg=${sbn.packageName}\nopPkg=${sbn.opPkg}"
        }
    }
}