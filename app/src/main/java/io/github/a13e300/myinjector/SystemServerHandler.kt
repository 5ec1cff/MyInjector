package io.github.a13e300.myinjector

import android.app.ActivityThread
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ServiceManager
import android.view.View
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.getParcelableExtraCompat
import java.io.File
import java.util.UUID

class SkipIf(private val cond: (p: MethodHookParam) -> Boolean) : XC_MethodHook() {
    override fun beforeHookedMethod(param: MethodHookParam) {
        if (cond(param)) param.result = null
    }
}

fun matchSimple(p: String, s: String?): Boolean {
    if (s == null) return false
    if (p.last() == '*') {
        return s.startsWith(p.removeSuffix("*"))
    }
    return p == s
}

class SystemServerHandler : IHook() {
    companion object {
        private const val CONFIG_PATH = "/data/misc"
        private const val CONFIG_PREFIX = "my_injector_system_"
    }

    private lateinit var config: SystemServerConfig
    private lateinit var thread: HandlerThread
    private lateinit var handler: Handler

    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        logD("handleLoadPackage: initialize")
        config = readConfig()
        hookNoWakePath()
        hookNoMiuiIntent()
        hookClipboardWhitelist()
        hookFixSync()
        setXSpace()
        hookForceNewTask()
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
            logD("findOrCreateConfigFile: created config $file")
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
                        logE("findConfig: remove bad config", it)
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
            findClass("miui.app.ActivitySecurityHelper"),
            "getCheckStartActivityIntent",
            hook
        )
        XposedBridge.hookAllMethods(
            findClass("miui.security.SecurityManager"),
            "getCheckStartActivityIntent",
            hook
        )
    }.onFailure {
        logE("hookNoWakePath: ", it)
    }

    private fun hookNoMiuiIntent() = runCatching {
        XposedHelpers.findAndHookMethod(
            "com.android.server.pm.PackageManagerServiceImpl",
            classLoader,
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
        logE("hookNoMiuiIntent: ", it)
    }

    private fun hookClipboardWhitelist() = runCatching {
        XposedBridge.hookAllMethods(
            findClass(
                "com.android.server.clipboard.ClipboardService"
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
        logE("hookClipboardWhitelist: ", it)
    }

    private fun hookFixSync() = runCatching {
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/wm/WindowState.java;l=5756;drc=4eb30271c338af7ee6abcbd2b7a9a0721db0595b
        // some gone accessibility windows does not get synced
        XposedBridge.hookAllMethods(
            findClass("com.android.server.wm.WindowState"),
            "isSyncFinished",
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam) {
                    if (!config.fixSync) return
                    if (param.thisObject.getObj(
                            "mViewVisibility"
                        ) != View.VISIBLE
                        && param.thisObject.getObj("mActivityRecord") == null
                    ) {
                        // XposedBridge.log("no wait on " + param.thisObject);
                        param.setResult(true)
                    }
                }
            }
        )
    }.onFailure {
        logE("hookFixSync: ", it)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setXSpace() {
        val enabled = config.xSpace
        val classXSpaceManager = findClass(
            "com.miui.server.xspace.XSpaceManagerServiceImpl"
        )
        classXSpaceManager.getObjSAs<MutableList<String>?>(
            "sCrossUserCallingPackagesWhiteList"
        )?.run {
            if (enabled) {
                add("com.android.shell")
                add("com.xiaomi.xmsf")
                logD("add required packages to whitelist")
            } else {
                remove("com.android.shell")
                remove("com.xiaomi.xmsf")
                logD("remove required packages to whitelist")
            }
        }
        classXSpaceManager.getObjSAs<MutableList<String>?>(
            "sPublicActionList"
        )?.run {
            clear()
            logD("clear publicActionList")
        }
    }

    private fun hookForceNewTask() = runCatching {
        val activityStarter =
            findClass("com.android.server.wm.ActivityStarter")
        val activityRecordClass = findClass("com.android.server.wm.ActivityRecord")
        XposedBridge.hookAllMethods(activityStarter, "executeRequest", object : XC_MethodHook() {
            override fun beforeHookedMethod(param: MethodHookParam) {
                if (!config.forceNewTask && !config.forceNewTaskDebug) return
                val request = param.args[0]
                val requestCode = request.getObjAs<Int>("requestCode")
                val intent = request.getObjAsN<Intent>("intent")
                val source = request.getObjAsN<String>("callingPackage")
                val targetInfo = request.getObjAsN<ActivityInfo>("activityInfo")
                val resultTo = request.getObj("resultTo")
                val sourceRecord = activityRecordClass.callS("isInAnyTask", resultTo)
                val sourceInfo = sourceRecord?.getObjAsN<ActivityInfo>("info")
                if (config.forceNewTaskDebug) {
                    val resultWho = request.getObj("resultWho")
                    logD("new intent start requestCode=$requestCode intent=$intent resultWho=$resultWho source=$source (${sourceInfo?.packageName}/${sourceInfo?.name}) target=${targetInfo?.packageName}/${targetInfo?.name}")
                }
                if (intent == null) return
                if (intent.flags.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0) return
                if (source == null) {
                    logW("forceNewTask: null source package!")
                    return
                }
                if (targetInfo == null) {
                    logW("forceNewTask: null target package!")
                    return
                }
                val selfStart = source == targetInfo.packageName
                config.forceNewTaskRulesList.forEach {
                    // TODO: support components
                    val anySource = it.sourcePackage == "*" && !selfStart
                    val anyTarget = it.targetPackage == "*" && !selfStart
                    val match = (it.sourcePackage == source || anySource)
                            && (it.sourceComponent.isEmpty() || matchSimple(
                        it.sourceComponent,
                        sourceInfo?.name
                    ))
                            && (it.targetPackage == targetInfo.packageName || anyTarget)
                            && (it.targetComponent.isEmpty() || matchSimple(
                        it.targetComponent,
                        targetInfo.name
                    ))
                    if (match) {
                        // do not handle startActivityForResult, except user forced.
                        val requestCode = request.getObjAs<Int>("requestCode")
                        if (requestCode >= 0 && !it.ignoreResult) {
                            logD("not handling requestCode >= 0: $requestCode")
                            return@forEach
                        }
                        if (config.forceNewTaskDebug) logI("forceNewTask: matched $it")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        if (it.useNewDocument) {
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                        }
                        return
                    }
                }
            }
        })
    }.onFailure {
        logE("hookForceNewTask: ", it)
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
                val pendingIntent =
                    intent.getParcelableExtraCompat("EXTRA_CREDENTIAL", PendingIntent::class.java)
                val caller = pendingIntent?.creatorPackage
                var oldXSpace = config.xSpace
                if (caller == BuildConfig.APPLICATION_ID) {
                    intent.getByteArrayExtra("EXTRA_CONFIG")?.let {
                        config = SystemServerConfig.parseFrom(it)
                        findOrCreateConfigFile().writeBytes(it)
                        logD("onReceive: update config $config")
                    }
                } else {
                    logE("onReceive: invalid caller $caller")
                }
                if (oldXSpace != config.xSpace) {
                    setXSpace()
                }
            }.onFailure {
                logE("onReceive: ", it)
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
            logD("registerBroadcast: registered")
        } catch (t: Throwable) {
            logE("registerBroadcast: ", t)
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
