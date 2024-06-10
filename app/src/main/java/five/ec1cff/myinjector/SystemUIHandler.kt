package five.ec1cff.myinjector

import android.app.INotificationManager
import android.content.Intent
import android.content.res.Resources
import android.os.ServiceManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import com.github.kyuubiran.ezxhelper.init.EzXHelperInit
import com.github.kyuubiran.ezxhelper.utils.findMethod
import com.github.kyuubiran.ezxhelper.utils.getObject
import com.github.kyuubiran.ezxhelper.utils.getObjectAs
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNull
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNullAs
import com.github.kyuubiran.ezxhelper.utils.hookAfter
import com.github.kyuubiran.ezxhelper.utils.invokeMethod
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.text.SimpleDateFormat
import java.util.Date

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
            parent.translationY
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
                        val topOffset = 20.dp2px(context.resources).toInt()
                        setMargins(px, px + topOffset, px, px)
                    }
                )
                /*
                modalWindowView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
                    modalWindowView.getChildAt(0)?.let { child ->
                        val position = IntArray(2)
                        child.getLocationInWindow(position)
                        val newPos = position[1] - 8.dp2px(context.resources)
                        Log.d("MyInjector", "layout change: move notification $newPos")
                        val ourView = parent.getChildAt(1)
                        ourView.translationY = newPos - ourView.height
                    }
                }*/
            }
            val tv = (parent.getChildAt(1) as ViewGroup).getChildAt(0) as TextView
            val entry = param.args[0] // NotificationEntry
            val sbn = entry?.getObjectOrNullAs<StatusBarNotification>("mSbn")
            // MIUI will fake Sbn.getPackageName, so we should get the real package name from the original field
            val field = XposedHelpers.findFieldIfExists(StatusBarNotification::class.java, "pkg")
                .also { it.isAccessible = true }
            if (sbn != null) {
                val realPkgName = field?.let { it.get(sbn) as String } ?: sbn.packageName
                val pkgName = sbn.packageName
                val channel = kotlin.runCatching {
                    nm.getNotificationChannel(
                        "com.android.systemui",
                        sbn.getObject("user").invokeMethod("getIdentifier") as Int,
                        realPkgName,
                        sbn.notification.channelId
                    )
                }.onFailure { Log.e("MyInjector", "getNotificationChannel", it) }.getOrNull()
                tv.text = StringBuilder().apply {
                    append("channelId=${sbn.notification.channelId}\n")
                    if (channel != null) append("channel=${channel.name}\n")
                    append("pkg=$pkgName\n")
                    if (realPkgName != pkgName) append("realPkg=$realPkgName\n")
                    append("opPkg=${sbn.opPkg}\n")
                    append("id=${sbn.id}\n")
                    append("initialPid=${XposedHelpers.getObjectField(sbn, "initialPid")}\n")
                    append(
                        "time=${sbn.postTime} (${
                            SimpleDateFormat.getDateTimeInstance().format(Date(sbn.postTime))
                        })"
                    )
                }
                (tv.parent as View).setOnClickListener {
                    runCatching {
                        val dependencyClass = XposedHelpers.findClass(
                            "com.android.systemui.Dependency",
                            lpparam.classLoader
                        )
                        val modalControllerClass = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.notification.modal.ModalController",
                            lpparam.classLoader
                        )
                        val commandQueueClass = XposedHelpers.findClass(
                            "com.android.systemui.statusbar.CommandQueue",
                            lpparam.classLoader
                        )

                        val mc = XposedHelpers.callStaticMethod(
                            dependencyClass,
                            "get",
                            modalControllerClass
                        )
                        XposedHelpers.callMethod(
                            mc,
                            "animExitModal",
                            50L,
                            true,
                            "MORE" /*com.miui.systemui.events.ModalExitMode.MORE.name*/,
                            false
                        )
                        val cq = XposedHelpers.callStaticMethod(
                            dependencyClass,
                            "get",
                            commandQueueClass
                        )
                        XposedHelpers.callMethod(cq, "animateCollapsePanels", 0, false)
                        // com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow$$ExternalSyntheticLambda1
                    }.onFailure {
                        Log.e("MyInjector", "exit modal: ", it)
                    }
                    // https://cs.android.com/android/platform/superproject/main/+/main:packages/apps/Settings/src/com/android/settings/notification/app/NotificationSettings.java;l=121;drc=d5137445c0d4067406cb3e38aade5507ff2fcd16
                    context.startActivity(
                        Intent(
                            Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS
                        )
                            .putExtra(Settings.EXTRA_APP_PACKAGE, realPkgName)
                            .putExtra(Settings.EXTRA_CHANNEL_ID, sbn.notification.channelId)
                            .putExtra(
                                "app_uid" /*Settings.EXTRA_APP_UID*/,
                                sbn.getObjectAs<Int>("uid")
                            )
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    )
                }
            } else {
                tv.text = "Failed to get StatusBarNotification!\nentry=$entry mSbn=${
                    entry?.getObjectOrNull("mSbn")
                }"
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