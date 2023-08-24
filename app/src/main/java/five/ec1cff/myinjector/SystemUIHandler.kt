package five.ec1cff.myinjector

import android.app.INotificationManager
import android.content.res.Resources
import android.os.ServiceManager
import android.service.notification.StatusBarNotification
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import android.widget.TextView
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.Log
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.getObject
import com.github.kyuubiran.ezxhelper.utils.getObjectAs
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNull
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNullAs
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import com.google.android.material.card.MaterialCardView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.callbacks.XC_LoadPackage

fun Float.dp2px(resources: Resources) =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, this, resources.displayMetrics)

fun Int.dp2px(resources: Resources) = toFloat().dp2px(resources)

class SystemUIHandler : IXposedHookLoadPackage {

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        EzXHelperInit.initHandleLoadPackage(lpparam)
        EzXHelperInit.setLogTag("myinjector-systemui")
        val nm by lazy {
            INotificationManager.Stub.asInterface(ServiceManager.getService("notification"))
        }
        findMethod("com.android.systemui.statusbar.notification.modal.ModalController") {
            name == "enterModal"
        }.hookAfter {
            val sbn = it.thisObject.getObjectOrNull("entry")
                ?.getObjectOrNullAs<StatusBarNotification>("mSbn") ?: return@hookAfter
            val root = it.thisObject.getObjectAs<ViewGroup>("modalWindowView")
            val child = root.getChildAt(0)
            if (child == null) {
                Log.e("no child at root $root")
                return@hookAfter
            }
            val packageCtx = root.context.createPackageContext(BuildConfig.APPLICATION_ID, 0)
            val ctx = ContextThemeWrapper(packageCtx, R.style.Theme_MyInjector)
            val f = MaterialCardView(ctx)
            val tv = TextView(ctx)
            val m = 8.dp2px(ctx.resources).toInt()
            tv.layoutParams = ViewGroup.MarginLayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(m, m, m, m)
            }
            f.addView(tv)
            val popup = PopupWindow(
                f,
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            popup.showAtLocation(root, Gravity.NO_GRAVITY, 0, 0)
            child.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(p0: View) {

                }

                override fun onViewDetachedFromWindow(p0: View) {
                    Log.d("dismissed")
                    popup.dismiss()
                    p0.removeOnAttachStateChangeListener(this)
                }
            })
            val channel = nm.getNotificationChannel(
                "com.android.systemui",
                sbn.getObject("user").invokeMethod("getIdentifier") as Int,
                sbn.packageName,
                sbn.notification.channelId
            )
            tv.text =
                "channel=${sbn.notification.channelId} (${channel.name})\npkg=${sbn.packageName}\nopPkg=${sbn.opPkg}"
        }
    }
}