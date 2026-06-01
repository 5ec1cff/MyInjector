package io.github.a13e300.myinjector

import android.annotation.SuppressLint
import android.app.ActivityThread
import android.app.INotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.os.Build
import android.os.ServiceManager
import android.provider.Settings
import android.service.notification.StatusBarNotification
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.dp2px
import io.github.a13e300.myinjector.arch.findClassOf
import io.github.a13e300.myinjector.arch.findView
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.getParcelableExtraCompat
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllNopIf
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.bridge.Unhook
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread

class MyFrameLayout(context: Context) : FrameLayout(context)

class SystemUIHandler : IHook() {
    private lateinit var config: SystemUIConfig
    private val pluginInitializeLock = Any()
    private var pluginHooked = false
    private val pluginInitializeHooks = mutableSetOf<Unhook>()

    private fun doHookDndToast(cl: ClassLoader) {
        synchronized(pluginInitializeLock) {
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
            pluginHooked = true
            logD("hooked dnd toast")
            runCatching {
                pluginInitializeHooks.forEach {
                    it.unhook()
                }
            }.onFailure {
                logE("unhook plugin initialize hooks", it)
            }
            pluginInitializeHooks.clear()
        }
    }

    private fun hookPlugin() = runCatching {
        val pluginInstanceClass = findClass("com.android.systemui.shared.plugins.PluginInstance")
        pluginInitializeHooks.addAll(pluginInstanceClass.hookAllAfter("loadPlugin") { param ->
            val ctx = param.thisObject.getObjAs<Context>("mPluginContext")
            val name = param.thisObject.getObjAs<ComponentName>("mComponentName")
            logD("hookPlugin: ctx=$ctx name=$name ctx.pkg=${ctx.packageName} ctx.cl=${ctx.classLoader}")
            if (name.packageName == "miui.systemui.plugin") {
                doHookDndToast(ctx.classLoader)
            }
        })
        val classLoaders = pluginInstanceClass.getObjSAs<Map<String, ClassLoader>>("sClassLoaders")
        classLoaders.get("miui.systemui.plugin")?.let {
            doHookDndToast(it)
        }
    }.onFailure { logE("hookCreatePkgContext: ", it) }

    private lateinit var configPath: File

    private fun loadConfig() = runCatching {
        if (configPath.isFile) {
            config = configPath.inputStream().use { SystemUIConfig.parseFrom(it) }
            logI("loaded config $config")
        } else {
            config = SystemUIConfig.getDefaultInstance()
            logI("use default config")
        }
    }.onFailure {
        logE("failed to load config from $configPath", it)
    }

    @SuppressLint("SetTextI18n")
    override fun onHook() {
        if (loadPackageParam.packageName != "com.android.systemui") return
        configPath =
            File(loadPackageParam.appInfo.deviceProtectedDataDir, "myinjector_config.proto")
        loadConfig()
        hookPlugin()
        hookNotificationInfo()
        hookSplashScreen()
    }

    override fun onUnhook(): Boolean {
        runCatching {
            ActivityThread.currentActivityThread().application.unregisterReceiver(receiver)
        }.onFailure {
            logE("unregister receiver", it)
        }
        removeMyView()
        return true
    }

    private var injectedRoot: ViewGroup? = null

    private fun removeMyView() {
        injectedRoot?.let { vg ->
            val latch = CountDownLatch(1)
            vg.post {
                runCatching {
                    val injected =
                        vg.findView { it.javaClass == MyFrameLayout::class.java } as? MyFrameLayout
                    if (injected != null) {
                        logD("removed injected $injected from $vg")
                        vg.removeView(injected)
                    }
                    injectedRoot = null
                }.onFailure {
                    logE("removeMyView", it)
                }
                latch.countDown()
            }
            latch.await()
        }
    }

    @SuppressLint("DiscouragedPrivateApi", "NewApi", "SetTextI18n")
    private fun hookNotificationInfo() {
        val nm by lazy {
            INotificationManager.Stub.asInterface(ServiceManager.getService("notification"))
        }
        val modalWindowViewClass =
            findClass("com.android.systemui.statusbar.notification.modal.ModalWindowView")
        modalWindowViewClass.hookAllAfter("enterModal") { param ->
                val modalWindowView = param.thisObject as FrameLayout
                val parent = modalWindowView.parent as FrameLayout
                val context = modalWindowView.context.applicationContext
            val findRoot =
                parent.findView { it.javaClass == MyFrameLayout::class.java } as? MyFrameLayout
            if (findRoot == null) {
                if (!config.showNotificationDetail) {
                    return@hookAllAfter
                }
                parent.addView(
                    MyFrameLayout(context).apply {
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
                injectedRoot = parent
                logD("injectedRoot = $parent")
            }

            val root = findRoot
                ?: parent.findView { it.javaClass == MyFrameLayout::class.java } as MyFrameLayout
                if (!config.showNotificationDetail) {
                    root.visibility = View.GONE
                    return@hookAllAfter
                } else {
                    root.visibility = View.VISIBLE
                }

                val tv = root.getChildAt(0) as TextView
                val entry = param.args[0] // NotificationEntry
                val sbn = entry?.getObj("mSbn") as? StatusBarNotification
                // MIUI will fake Sbn.getPackageName, so we should get the real package name from the original field
                val field = try {
                    StatusBarNotification::class.java.getDeclaredField("pkg")
                        .also { it.isAccessible = true }
                } catch (_: NoSuchFieldException) {
                    null
                }
                if (sbn != null) {
                    val realPkgName = field?.let { it.get(sbn) as String } ?: sbn.packageName
                    val pkgName = sbn.packageName
                    val channel = kotlin.runCatching {
                        nm.getNotificationChannel(
                            "com.android.systemui",
                            sbn.getObj("user").call("getIdentifier") as Int,
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
                            "initialPid=${sbn.getObj("initialPid")}\n"
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
                    val commandQueueClass = findClass(
                        "com.android.systemui.statusbar.CommandQueue"
                    )
                    val dep by lazy { dependencyClass.getObjS("sDependency") }
                    fun getModalControllerLegacy(): Any {
                        val modalControllerClass =
                            findClass("com.android.systemui.statusbar.notification.modal.ModalController")
                        return dep.call("getDependencyInner", modalControllerClass)!!
                    }

                    fun getModalController(): Any? {
                        runCatching {
                            return modalWindowView.getObjAsN("mModalController")
                        }
                        return null
                    }
                    (tv.parent as View).setOnClickListener {
                        runCatching {
                            val mc = getModalController() ?: getModalControllerLegacy()
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
        val hasIsExpandable = clz.declaredMethods.any { it.name == "isExpandable" }
        val rootViewClz = findClass("com.android.systemui.shade.NotificationShadeWindowView")
        clz.hookAllBefore("isExpanded") { param ->
            if (!config.alwaysExpandNotification) return@hookAllBefore
            if ((param.thisObject as View).rootView.javaClass == rootViewClz) {
                param.result = if (hasIsExpandable) param.thisObject.call("isExpandable")
                else param.thisObject.call("isExpandable$1")
            }
        }
    }.onFailure {
        logE("hookExpandNotification", it)
    }

    companion object {
        private val sBlackDrawable = ColorDrawable(Color.BLACK)

        private fun fixDrawable(d: Drawable): Drawable {
            if (d is ColorDrawable) {
                if (d.color == Color.WHITE) {
                    logD("replace white color drawable with black")
                    return sBlackDrawable
                }
            } else if (d is LayerDrawable) {
                val dup = d.mutate() as LayerDrawable
                val len = dup.numberOfLayers
                for (i in 0 until len) {
                    val old = dup.getDrawable(i)
                    val newDrawable = fixDrawable(old)
                    if (newDrawable !== old) {
                        logD("replace layer $i")
                    }
                    dup.setDrawable(i, newDrawable)
                }
                return dup
            }

            logD("nothing to fix")
            return d
        }
    }

    private fun hookSplashScreen() = runCatching {
        findClass("android.window.SplashScreenView\$Builder").hookAllBefore("build") { param ->
            if (!config.fixWhiteSplash) return@hookAllBefore
            val context = param.thisObject.getObjAs<Context>("mContext")
            val uiModeNight =
                context.resources.configuration.uiMode.and(Configuration.UI_MODE_NIGHT_MASK)
            logD("uiModeNight: $uiModeNight")
            if (uiModeNight == Configuration.UI_MODE_NIGHT_YES) {
                // This is intended to fix bilibili's white splash screen
                // TODO: fix more

                // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/core/java/android/window/SplashScreenView.java;l=248;drc=b46cbdac287350ce7d4f5bd1b5f656922c1a2186
                // https://cs.android.com/android/platform/superproject/+/android-latest-release:frameworks/base/libs/WindowManager/Shell/src/com/android/wm/shell/startingsurface/SplashscreenContentDrawer.java;l=506-524;drc=80c4470f99351053b43bdfd778d087a691b508bb
                val overlayDrawable =
                    param.thisObject.getObjAsN<Drawable>("mOverlayDrawable") ?: return@hookAllBefore
                param.thisObject.setObj("mOverlayDrawable", fixDrawable(overlayDrawable))
            }
        }
    }.onFailure {
        logE("hookSplashScreen", it)
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
                        logI("onReceive: update config $config")
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
        thread {
            try {
                while (true) {
                    Thread.sleep(1000)
                    val ctx = ActivityThread.currentActivityThread().application ?: continue
                    val flags =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
                    ctx.registerReceiver(
                        receiver,
                        IntentFilter("io.github.a13e300.myinjector.UPDATE_SYSTEMUI_CONFIG"),
                        null,
                        null,
                        flags
                    )
                    logI("registerBroadcast: registered")
                    break
                }
            } catch (t: Throwable) {
                logE("registerBroadcast: ", t)
            }
        }
    }
}