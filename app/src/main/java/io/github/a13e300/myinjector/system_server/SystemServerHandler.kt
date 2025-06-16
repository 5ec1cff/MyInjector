package io.github.a13e300.myinjector.system_server

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import android.view.View
import android.view.WindowInsetsController
import io.github.a13e300.myinjector.SystemServerConfig
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.findClass
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllNopIf
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.bridge.HotLoadHook
import io.github.a13e300.myinjector.bridge.LoadPackageParam
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.logI
import io.github.a13e300.myinjector.logW
import java.io.File
import java.lang.Long
import java.util.UUID
import kotlin.Boolean
import kotlin.Int
import kotlin.String
import kotlin.Suppress
import kotlin.let
import kotlin.onFailure
import kotlin.run
import kotlin.runCatching

fun matchSimple(p: String, s: String?): Boolean {
    if (s == null) return false
    if (p.last() == '*') {
        return s.startsWith(p.removeSuffix("*"))
    }
    return p == s
}

class SystemServerHandler : HotLoadHook() {
    companion object {
        private const val CONFIG_PATH = "/data/misc"
        private const val CONFIG_PREFIX = "my_injector_system_"

    }

    private lateinit var config: SystemServerConfig
    private lateinit var broadcastManager: BroadcastManager

    private lateinit var loadPackageParam: LoadPackageParam
    private val hooks = mutableListOf<Unhook>()

    fun findClass(name: String) = loadPackageParam.classLoader.findClass(name)

    override fun onLoad(param: LoadPackageParam) {
        loadPackageParam = param
        logI("hook system")
        config = readConfig()
        logI("config for system server: $config")
        hookNoWakePath()
        hookNoMiuiIntent()
        hookClipboardWhitelist()
        hookFixSync()
        setXSpace(config.xSpace)
        hookForceNewTask()
        hookStatusBarAppearance()
        broadcastManager = BroadcastManager("MyInjector-SystemServer")
        runConfigListener()
    }

    override fun onUnload() {
        broadcastManager.unregister(receiver)
        broadcastManager.stop()
        setXSpace(false)
        hooks.forEach { it.unhook() }
        hooks.clear()
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
            logI("findOrCreateConfigFile: created config $file")
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
        hooks.addAll(findClass("miui.app.ActivitySecurityHelper").hookAllNopIf("getCheckStartActivityIntent") {
            config.noWakePath
        })
        hooks.addAll(findClass("miui.security.SecurityManager").hookAllNopIf("getCheckStartActivityIntent") {
            config.noWakePath
        })
    }.onFailure {
        logE("hookNoWakePath: ", it)
    }

    private fun hookNoMiuiIntent() = runCatching {
        hooks.add(
            findClass("com.android.server.pm.PackageManagerServiceImpl").hookBefore(
            "hookChooseBestActivity",
            Intent::class.java,
            String::class.java,
                Long.TYPE,
            List::class.java,
            Integer.TYPE,
            ResolveInfo::class.java
        ) { param ->
            param.result = param.args[5] // defaultValue
            })
    }.onFailure {
        logE("hookNoMiuiIntent: ", it)
    }

    private fun hookClipboardWhitelist() = runCatching {
        hooks.addAll(
            findClass("com.android.server.clipboard.ClipboardService")
            .hookAllBefore("clipboardAccessAllowed") { param ->
                if (!config.clipboardWhitelist) return@hookAllBefore
                val pkg = param.args[1]?.toString() ?: return@hookAllBefore
                if (config.clipboardWhitelistPackagesList.contains(pkg)) param.result = true
            })
    }.onFailure {
        logE("hookClipboardWhitelist: ", it)
    }

    private fun hookFixSync() = runCatching {
        // https://cs.android.com/android/platform/superproject/main/+/main:frameworks/base/services/core/java/com/android/server/wm/WindowState.java;l=5756;drc=4eb30271c338af7ee6abcbd2b7a9a0721db0595b
        // some gone accessibility windows does not get synced
        hooks.addAll(
            findClass("com.android.server.wm.WindowState")
            .hookAllBefore("isSyncFinished") { param ->
                if (!config.fixSync) return@hookAllBefore
                if (param.thisObject.getObj(
                        "mViewVisibility"
                    ) != View.VISIBLE
                    && param.thisObject.getObj("mActivityRecord") == null
                ) {
                    // XposedBridge.log("no wait on " + param.thisObject);
                    param.result = true
                }
            })
    }.onFailure {
        logE("hookFixSync: ", it)
    }

    @Suppress("UNCHECKED_CAST")
    private fun setXSpace(enabled: Boolean) {
        runCatching {
            val classXSpaceManager = findClass(
                "com.miui.server.xspace.XSpaceManagerServiceImpl"
            )
            classXSpaceManager.getObjSAs<MutableList<String>?>(
                "sCrossUserCallingPackagesWhiteList"
            )?.run {
                if (enabled) {
                    add("com.android.shell")
                    add("com.xiaomi.xmsf")
                    logI("add required packages to whitelist")
                } else {
                    remove("com.android.shell")
                    remove("com.xiaomi.xmsf")
                    logI("remove required packages to whitelist")
                }
            }
            if (enabled) {
                classXSpaceManager.getObjSAs<MutableList<String>?>(
                    "sPublicActionList"
                )?.run {
                    clear()
                    logI("clear publicActionList")
                }
            }
        }.onFailure {
            logE("setXSpace $enabled", it)
        }
    }

    private fun hookForceNewTask() = runCatching {
        val activityStarter =
            findClass("com.android.server.wm.ActivityStarter")
        val activityRecordClass = findClass("com.android.server.wm.ActivityRecord")
        hooks.addAll(activityStarter.hookAllBefore("executeRequest") { param ->
            if (!config.forceNewTask && !config.forceNewTaskDebug) return@hookAllBefore
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
                logI("new intent start requestCode=$requestCode intent=$intent resultWho=$resultWho source=$source (${sourceInfo?.packageName}/${sourceInfo?.name}) target=${targetInfo?.packageName}/${targetInfo?.name}")
            }
            if (intent == null) return@hookAllBefore
            if (intent.flags.and(Intent.FLAG_ACTIVITY_NEW_TASK) != 0) return@hookAllBefore
            if (source == null) {
                logW("forceNewTask: null source package!")
                return@hookAllBefore
            }
            if (targetInfo == null) {
                logW("forceNewTask: null target package!")
                return@hookAllBefore
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
                        if (config.forceNewTaskDebug) logI("not handling requestCode >= 0: $requestCode")
                        return@forEach
                    }
                    if (config.forceNewTaskDebug) logI("forceNewTask: matched $it")
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    if (it.useNewDocument) {
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT)
                    }
                    return@hookAllBefore
                }
            }
        })
    }.onFailure {
        logE("hookForceNewTask: ", it)
    }

    private fun runConfigListener() {
        broadcastManager.register(
            IntentFilter("io.github.a13e300.myinjector.UPDATE_SYSTEM_CONFIG"),
            receiver
        )
    }

    private val receiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            intent ?: return
            runCatching {
                if (!isCallerTrusted(intent)) return
                val oldXSpace = config.xSpace
                intent.getByteArrayExtra("EXTRA_CONFIG")?.let {
                    config = SystemServerConfig.parseFrom(it)
                    findOrCreateConfigFile().writeBytes(it)
                    logI("onReceive: update config $config")
                }
                if (oldXSpace != config.xSpace) {
                    setXSpace(config.xSpace)
                }
            }.onFailure {
                logE("onReceive: ", it)
            }
        }
    }


    @SuppressLint("InlinedApi")
    private fun hookStatusBarAppearance() = runCatching {
        val displayPolicy = findClass("com.android.server.wm.DisplayPolicy")
        hooks.addAll(
            displayPolicy.hookAllBefore(
            "updateSystemBarAttributes",
            cond = { config.overrideStatusBar }) { param ->
            val windowState = param.thisObject.getObj("mFocusedWindow") ?: return@hookAllBefore
            val activityRecord = windowState.getObj("mActivityRecord") ?: return@hookAllBefore
            val currentActivity = activityRecord.getObjAsN<ComponentName>("mActivityComponent")
                ?: return@hookAllBefore

            val rules = config.overrideStatusBarRulesList
            for (rule in rules) {
                if (currentActivity.packageName != rule.`package`) continue
                if (rule.component.isEmpty() || currentActivity.className == rule.component) {
                    // logD("matched $rule")
                    val attr = windowState.getObj("mAttrs")
                    val insetFlags = attr.getObj("insetsFlags")
                    val appearance = insetFlags.getObjAs<Int>("appearance")
                    val newAppearance = if (rule.light)
                        appearance.or(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
                    else appearance.and(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS.inv())
                    insetFlags.setObj("appearance", newAppearance)
                    return@hookAllBefore
                }
            }
            })
    }.onFailure {
        logE("hookStatusBarAppearance", it)
    }
}
