package io.github.a13e300.myinjector

import android.app.ActivityThread
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ServiceManager
import android.util.Log
import android.view.View
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.File
import java.util.UUID

class SkipIf(private val cond: (p: MethodHookParam) -> Boolean) : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        if (cond(param)) param.result = null
    }
}

class SystemServerHandler : IXposedHookLoadPackage {
    companion object {
        private const val CONFIG_PATH = "/data/misc"
        private const val CONFIG_PREFIX = "my_injector_system_"
        private const val TAG = "myinjector-system"
    }

    private lateinit var config: SystemServerConfig
    private lateinit var lpparam: XC_LoadPackage.LoadPackageParam
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d(TAG, "handleLoadPackage: initialize")
        config = readConfig()
        this.lpparam = lpparam
        hookNoWakePath()
        hookNoMiuiIntent()
        hookClipboardWhitelist()
        hookFixSync()
        setXSpace()
        runConfigListener()
    }

    private fun findOrCreateConfigFile(): File {
        val dir = File(CONFIG_PATH)
        var file: File? = null
        dir.listFiles()?.let { files ->
            for (f in files) {
                if (f.name.startsWith(CONFIG_PREFIX) && f.isFile) {
                    if (file == null) {
                        file = f
                    } else {
                        f.delete()
                    }
                }
            }
        }

        if (file == null) {
            file = File(dir, "${CONFIG_PREFIX}${UUID.randomUUID()}")
            Log.d(TAG, "findOrCreateConfigFile: created config $file")
            file.createNewFile()
        }

        return file
    }

    private fun readConfig(): SystemServerConfig {
        val dir = File(CONFIG_PATH)
        dir.listFiles()?.let { files ->
            for (f in files) {
                if (f.name.startsWith(CONFIG_PREFIX) && f.isFile) {
                    runCatching {
                        return f.inputStream().use { SystemServerConfig.parseFrom(it) }
                    }.onFailure {
                        Log.e(TAG, "findConfig: remove bad config", it)
                        f.delete()
                    }
                }
            }
        }
        return SystemServerConfig.getDefaultInstance()
    }

    private fun hookNoWakePath() = runCatching {
        val hook = SkipIf { _ -> config.noWakePath }
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("miui.app.ActivitySecurityHelper", lpparam.classLoader),
            "getCheckStartActivityIntent",
            hook
        )
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("miui.security.SecurityManager", lpparam.classLoader),
            "getCheckStartActivityIntent",
            hook
        )
    }.onFailure {
        Log.e(TAG, "hookNoWakePath: ", it)
    }

    private fun hookNoMiuiIntent() = runCatching {
        XposedHelpers.findAndHookMethod(
            "com.android.server.pm.PackageManagerServiceImpl",
            lpparam.classLoader,
            "hookChooseBestActivity",
            Intent::class.java,
            String::class.java,
            java.lang.Long.TYPE,
            List::class.java,
            Integer.TYPE,
            ResolveInfo::class.java, object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    param.result = param.args[5] // defaultValue
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookNoMiuiIntent: ", it)
    }

    private fun hookClipboardWhitelist() = runCatching {
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass(
                "com.android.server.clipboard.ClipboardService",
                lpparam.classLoader
            ),
            "clipboardAccessAllowed",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!config.clipboardWhitelist) return
                    var pkg = param.args[1]?.toString() ?: return
                    if (config.clipboardWhitelistPackagesList.contains(pkg)) param.setResult(true)
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookClipboardWhitelist: ", it)
    }

    private fun hookFixSync() = runCatching {
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/wm/WindowState.java;l=5756;drc=4eb30271c338af7ee6abcbd2b7a9a0721db0595b
        // some gone accessibility windows does not get synced
        XposedBridge.hookAllMethods(
            XposedHelpers.findClass("com.android.server.wm.WindowState", lpparam.classLoader),
            "isSyncFinished",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!config.fixSync) return
                    if (XposedHelpers.getObjectField(
                            param.thisObject,
                            "mViewVisibility"
                        ) != View.VISIBLE
                        && XposedHelpers.getObjectField(param.thisObject, "mActivityRecord") == null
                    ) {
                        // XposedBridge.log("no wait on " + param.thisObject);
                        param.setResult(true)
                    }
                }
            }
        )
    }.onFailure {
        Log.e(TAG, "hookFixSync: ", it)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setXSpace() {
        val enabled = config.xSpace
        val classXSpaceManager = XposedHelpers.findClass(
            "com.miui.server.xspace.XSpaceManagerServiceImpl",
            lpparam.classLoader
        );
        (XposedHelpers.getStaticObjectField(
            classXSpaceManager,
            "sCrossUserCallingPackagesWhiteList"
        ) as? MutableList<String>)?.run {
            if (enabled) {
                add("com.android.shell")
                add("com.xiaomi.xmsf")
                Log.d(TAG, "add required packages to whitelist")
            } else {
                remove("com.android.shell")
                remove("com.xiaomi.xmsf")
                Log.d(TAG, "remove required packages to whitelist")
            }
        }
        (XposedHelpers.getStaticObjectField(
            classXSpaceManager,
            "sPublicActionList"
        ) as? MutableList<String>)?.run {
            clear()
            Log.d(TAG, "clear publicActionList")
        }
    }

    private fun runConfigListener() {
        thread = HandlerThread("MyInjector")
        thread.start()
        handler = Handler(thread.looper)
        handler.post(waitAms)
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            intent ?: return
            runCatching {
                val pendingIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra("EXTRA_CREDENTIAL", PendingIntent::class.java)
                } else {
                    intent.getParcelableExtra<PendingIntent>("EXTRA_CREDENTIAL")
                }
                val caller = pendingIntent?.creatorPackage
                var oldXSpace = config.xSpace
                if (caller == BuildConfig.APPLICATION_ID) {
                    intent.getByteArrayExtra("EXTRA_CONFIG")?.let {
                        config = SystemServerConfig.parseFrom(it)
                        findOrCreateConfigFile().writeBytes(it)
                        Log.d(TAG, "onReceive: update config $config")
                    }
                } else {
                    Log.e(TAG, "onReceive: invalid caller $caller")
                }
                if (oldXSpace != config.xSpace) {
                    setXSpace()
                }
            }.onFailure {
                Log.e(TAG, "onReceive: ", it)
            }
        }
    }

    private val registerBroadcast: Runnable = Runnable {
        try {
            val ctx = ActivityThread.currentActivityThread().systemContext as Context
            val flags =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
            ctx.registerReceiver(
                receiver,
                IntentFilter("io.github.a13e300.myinjector.UPDATE_SYSTEM_CONFIG"),
                null,
                handler,
                flags
            )
            Log.d(TAG, "registerBroadcast: registered")
        } catch (t: Throwable) {
            Log.e(TAG, "registerBroadcast: ", t)
            handler.postDelayed(registerBroadcast, 1000)
        }
    }

    private val waitAms: Runnable = Runnable {
        try {
            if (ServiceManager.getService("activity") != null) {
                handler.post(registerBroadcast)
                return@Runnable
            }
        } catch (_: Throwable) {

        }
        handler.postDelayed(waitAms, 1000)
    }
}
