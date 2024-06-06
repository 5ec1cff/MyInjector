package five.ec1cff.myinjector

import android.app.INotificationManager
import android.content.Intent
import android.content.res.Resources
import android.os.ServiceManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.getObject
import com.github.kyuubiran.ezxhelper.utils.getObjectAs
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNullAs
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
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
        findMethod("com.android.systemui.statusbar.notification.modal.ModalWindowView") {
            name == "enterModal"
        }.hookAfter { param ->
            val modalWindowView = param.thisObject as FrameLayout
            val parent = modalWindowView.parent as FrameLayout
            val context = modalWindowView.context.applicationContext
            if (parent.childCount == 1) {
                parent.addView(
                    FrameLayout(context).apply {
                        setBackgroundColor(0x99ffffff.toInt())
                        addView(
                            TextView(context),
                            ViewGroup.MarginLayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT
                            ).apply {
                                val px = 8.dp2px(context.resources).toInt()
                                setMargins(px, px, px, px)
                            }
                        )
                    },
                    ViewGroup.MarginLayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply {
                        val px = 8.dp2px(context.resources).toInt()
                        setMargins(px, px, px, px)
                    }
                )
            }
            val tv = (parent.getChildAt(1) as ViewGroup).getChildAt(0) as TextView
            val sbn = param.args[0]
                ?.getObjectOrNullAs<StatusBarNotification>("mSbn") ?: return@hookAfter
            val channel = nm.getNotificationChannel(
                "com.android.systemui",
                sbn.getObject("user").invokeMethod("getIdentifier") as Int,
                sbn.packageName,
                sbn.notification.channelId
            )
            tv.text =
                "channel=${sbn.notification.channelId} (${channel.name})\npkg=${sbn.packageName}\nopPkg=${sbn.opPkg}"
            (tv.parent as View).setOnClickListener {
                // https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/Settings/src/com/android/settings/notification/app/NotificationSettings.java;l=121;drc=d5137445c0d4067406cb3e38aade5507ff2fcd16
                context.startActivity(
                    Intent(
                        Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                    )
                        .putExtra(Settings.EXTRA_APP_PACKAGE, sbn.packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, sbn.notification.channelId)
                        .putExtra("app_uid" /*Settings.EXTRA_APP_UID*/, sbn.getObjectAs<Int>("uid"))
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        // TODO: use resources
        // https://github.com/yujincheng08/BiliRoaming/blame/637bd4312c366d1417ea7109f924d7e7aa51e99b/app/build.gradle.kts#L115
        //     androidResources {
        //        additionalParameters += arrayOf("--allow-reserved-package-id", "--package-id", "0x23")
        //    }
        /*
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
        }*/
    }
}