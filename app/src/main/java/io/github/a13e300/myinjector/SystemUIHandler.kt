package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.INotificationManager
import android.content.Intent
import android.os.ServiceManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import dalvik.system.PathClassLoader
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.dp2px
import io.github.a13e300.myinjector.arch.findClass
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjS
import java.text.SimpleDateFormat
import java.util.Date


class SystemUIHandler : IHook() {
    private fun hookPlugin() = runCatching {
        val parentCLClass = findClass(
            "com.android.systemui.shared.plugins.PluginManagerImpl\$ClassLoaderFilter"
        )
        XposedHelpers.findAndHookConstructor(
            PathClassLoader::class.java,
            String::class.java, String::class.java, ClassLoader::class.java,
            object : XC_MethodHook() {
                override fun afterHookedMethod(param: MethodHookParam) {
                    if (param.args[2].javaClass != parentCLClass) return
                    val cl = param.thisObject as ClassLoader

                    logD("in sysui plugin", Throwable())
                    runCatching {
                        XposedBridge.hookAllMethods(
                            cl.findClass(
                                "com.android.systemui.miui.volume.MiuiVolumeDialogImpl\$SilenceModeObserver"
                            ), "showToastOrStatusBar", XC_MethodReplacement.DO_NOTHING
                        )
                    }.onFailure {
                        logE("hook showToast", it)
                    }
                }
            }
        )
    }.onFailure { logE("hookCreatePkgContext: ", it) }

    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        hookPlugin()
        val nm by lazy {
            INotificationManager.Stub.asInterface(ServiceManager.getService("notification"))
        }
        XposedBridge.hookAllMethods(
            findClass("com.android.systemui.statusbar.notification.modal.ModalWindowView"),
            "enterModal",
            object : XC_MethodHook() {
                @SuppressLint("SetTextI18n")
                override fun afterHookedMethod(param: MethodHookParam) {
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
                    }

                    val tv = (parent.getChildAt(1) as ViewGroup).getChildAt(0) as TextView
                    val entry = param.args[0] // NotificationEntry
                    val sbn = entry?.let {
                        XposedHelpers.getObjectField(
                            it,
                            "mSbn"
                        )
                    } as? StatusBarNotification
                    // MIUI will fake Sbn.getPackageName, so we should get the real package name from the original field
                    val field =
                        XposedHelpers.findFieldIfExists(StatusBarNotification::class.java, "pkg")
                            .also { it.isAccessible = true }
                    if (sbn != null) {
                        val realPkgName = field?.let { it.get(sbn) as String } ?: sbn.packageName
                        val pkgName = sbn.packageName
                        val channel = kotlin.runCatching {
                            nm.getNotificationChannel(
                                "com.android.systemui",
                                XposedHelpers.callMethod(
                                    XposedHelpers.getObjectField(sbn, "user"),
                                    "getIdentifier"
                                ) as Int,
                                realPkgName,
                                sbn.notification.channelId
                            )
                        }.onFailure { logE("getNotificationChannel", it) }
                            .getOrNull()
                        tv.text = StringBuilder().apply {
                            append("channelId=${sbn.notification.channelId}\n")
                            if (channel != null) append("channel=${channel.name}\n")
                            append("pkg=$pkgName\n")
                            if (realPkgName != pkgName) append("realPkg=$realPkgName\n")
                            if (sbn.opPkg != pkgName) append("opPkg=${sbn.opPkg}\n")
                            append("id=${sbn.id}\n")
                            append(
                                "initialPid=${
                                    XposedHelpers.getObjectField(
                                        sbn,
                                        "initialPid"
                                    )
                                }\n"
                            )
                            append(
                                "time=${sbn.postTime} (${
                                    SimpleDateFormat.getDateTimeInstance()
                                        .format(Date(sbn.postTime))
                                })"
                            )
                        }
                        val dependencyClass = findClass(
                            "com.android.systemui.Dependency"
                        )
                        val modalControllerClass = findClass(
                            "com.android.systemui.statusbar.notification.modal.ModalController"
                        )
                        val commandQueueClass = findClass(
                            "com.android.systemui.statusbar.CommandQueue"
                        )
                        val dep by lazy { dependencyClass.getObjS("sDependency") }
                        (tv.parent as View).setOnClickListener {
                            runCatching {
                                val mc = dep.call("getDependencyInner", modalControllerClass)
                                mc.call(
                                    "animExitModal",
                                    50L,
                                    true,
                                    "MORE" /*com.miui.systemui.events.ModalExitMode.MORE.name*/,
                                    false
                                )
                                val cq = dep.call("getDependencyInner", commandQueueClass)
                                cq.call("animateCollapsePanels", 0, false)
                                // com.android.systemui.statusbar.notification.row.MiuiNotificationMenuRow$$ExternalSyntheticLambda1
                            }.onFailure {
                                logE("exit modal: ", it)
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
                                        sbn.getObjAs<Int>("uid")
                                    )
                                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                            )
                        }
                    } else {
                        tv.text = "Failed to get StatusBarNotification!\nentry=$entry mSbn=${
                            entry.getObj("mSbn")
                        }"
                    }
                }
            }
        )
    }
}