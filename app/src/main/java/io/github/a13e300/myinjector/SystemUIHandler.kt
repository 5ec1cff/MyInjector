package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.app.INotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.ServiceManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import dalvik.system.PathClassLoader
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.dp2px
import io.github.a13e300.myinjector.arch.findClassOf
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.getParcelableExtraCompat
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllNopIf
import io.github.a13e300.myinjector.arch.hookCAfter
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date


class SystemUIHandler : IHook() {
    private lateinit var config: SystemUIConfig
    private fun hookPlugin() = runCatching {
        val parentCLClass = findClass(
            "com.android.systemui.shared.plugins.PluginManagerImpl\$ClassLoaderFilter"
        )
        PathClassLoader::class.java.hookCAfter(
            String::class.java,
            String::class.java,
            ClassLoader::class.java
        ) { param ->
            if (param.args[2].javaClass != parentCLClass) return@hookCAfter
            val cl = param.thisObject as ClassLoader

            // logD("in sysui plugin", Throwable())
            runCatching {
                // hook to not show status bar notification when silence mode changed
                // to debug: cmd notification set_dnd on/off
                cl.findClassOf(
                    "com.android.systemui.miui.volume.VolumePanelViewController\$SilenceModeObserver",
                    "com.android.systemui.miui.volume.MiuiVolumeDialogImpl\$SilenceModeObserver"
                ).hookAllNopIf("showToastOrStatusBar") {
                    config.noDndNotification
                }
            }.onFailure {
                logE("hook showToast", it)
            }
        }
    }.onFailure { logE("hookCreatePkgContext: ", it) }

    private lateinit var configPath: File

    private fun loadConfig() = runCatching {
        if (configPath.isFile) {
            config = configPath.inputStream().use { SystemUIConfig.parseFrom(it) }
            logD("loaded config $config")
        } else {
            config = SystemUIConfig.getDefaultInstance()
            logD("use default config")
        }
    }.onFailure {
        logE("failed to load config from $configPath", it)
    }

    @SuppressLint("SetTextI18n")
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.android.systemui") return
        configPath = File(lpparam.appInfo.deviceProtectedDataDir, "myinjector_config.proto")
        loadConfig()
        hookPlugin()
        hookNotificationInfo()
    }

    private fun hookNotificationInfo() {
        val nm by lazy {
            INotificationManager.Stub.asInterface(ServiceManager.getService("notification"))
        }
        findClass("com.android.systemui.statusbar.notification.modal.ModalWindowView")
            .hookAllAfter("enterModal") { param ->
                val modalWindowView = param.thisObject as FrameLayout
                val parent = modalWindowView.parent as FrameLayout
                val context = modalWindowView.context.applicationContext
                if (parent.childCount == 1) {
                    if (!config.showNotificationDetail) {
                        return@hookAllAfter
                    }
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

                val root = parent.getChildAt(1) as ViewGroup
                if (!config.showNotificationDetail) {
                    root.visibility = View.GONE
                    return@hookAllAfter
                } else {
                    root.visibility = View.VISIBLE
                }

                val tv = root.getChildAt(0) as TextView
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

        hookExpandNotification()
        registerBroadcast()
    }

    private fun hookExpandNotification() = runCatching {
        val clz =
            findClass("com.android.systemui.statusbar.notification.row.ExpandableNotificationRow")
        val rootViewClz = findClass("com.android.systemui.shade.NotificationShadeWindowView")
        clz.hookAllBefore("isExpanded") { param ->
            if (!config.alwaysExpandNotification) return@hookAllBefore
            if ((param.thisObject as View).rootView.javaClass == rootViewClz) {
                param.result = param.thisObject.call("isExpandable$1")
            }
        }
    }.onFailure {
        logE("hookExpandNotification", it)
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent ?: return
            runCatching {
                val pendingIntent =
                    intent.getParcelableExtraCompat("EXTRA_CREDENTIAL", PendingIntent::class.java)
                val caller = pendingIntent?.creatorPackage
                if (caller == BuildConfig.APPLICATION_ID) {
                    intent.getByteArrayExtra("EXTRA_CONFIG")?.let {
                        config = SystemUIConfig.parseFrom(it)
                        configPath.writeBytes(it)
                        logD("onReceive: update config $config")
                    }
                } else {
                    logE("onReceive: invalid caller $caller")
                }
            }.onFailure {
                logE("onReceive: ", it)
            }
        }
    }

    private fun registerBroadcast() {
        try {
            val ctx = ActivityThread.currentActivityThread().systemContext as Context
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
            ctx.registerReceiver(
                receiver,
                IntentFilter("io.github.a13e300.myinjector.UPDATE_SYSTEMUI_CONFIG"),
                null,
                null,
                flags
            )
            logD("registerBroadcast: registered")
        } catch (t: Throwable) {
            logE("registerBroadcast: ", t)
        }
    }
}