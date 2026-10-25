package io.github.a13e300.myinjector.system_server

import android.app.ActivityThread
import android.app.IActivityManager
import android.app.PendingIntent
import android.app.TaskStackBuilder
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.ServiceManager
import android.util.AtomicFile
import android.widget.Toast
import io.github.a13e300.myinjector.BuildConfig
import io.github.a13e300.myinjector.MiuiHomeConfig
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.getParcelableExtraCompat
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.logI
import java.io.File

/** Binder-authenticated bridge for the DEX-less launcher. No exported service is added. */
class MiuiHomeRuntime : IHook() {
    private companion object {
        const val LAUNCHER = "com.miui.home"
        const val PREFIX = "io.github.a13e300.myinjector.miuihome."
        const val COMMAND = PREFIX + "COMMAND"
        const val COMMAND_PACKAGE = PREFIX + "command"
    }

    @Volatile private var config = MiuiHomeConfig.getDefaultInstance()
    @Volatile private var ready = false
    private val configFile = AtomicFile(File("/data/system/myinjector_miuihome.pb"))
    private var broadcasts: BroadcastManager? = null
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private val seen = LinkedHashMap<String, Unit>() // accessed only on worker
    private val systemContext: Context
        get() = ActivityThread.currentActivityThread().systemContext

    override fun onHook() {
        if (findClassOrNull("com.android.server.am.ProcessListStub") == null) return
        runCatching { config = configFile.openRead().use { MiuiHomeConfig.parseFrom(it) } }
        worker = HandlerThread("MyInjector-MiuiHome").also { it.start() }
        handler = Handler(worker!!.looper)
        broadcasts = BroadcastManager("MyInjector-MiuiHomeConfig").also {
            it.register(IntentFilter("io.github.a13e300.myinjector.UPDATE_MIUI_HOME_CONFIG"), receiver)
        }
        // The native entry asks for three booleans through the existing PM Binder.
        // Our private feature namespace never reaches normal device-feature logic.
        runCatching {
            findClass("com.android.server.pm.PackageManagerService").hookBefore(
                "hasSystemFeature", String::class.java, Integer.TYPE
            ) { param ->
                val feature = param.args[0] as? String ?: return@hookBefore
                if (!feature.startsWith(PREFIX)) return@hookBefore
                param.result = isLauncherCaller() && when (feature.removePrefix(PREFIX)) {
                    "v1" -> ready
                    "dragKill" -> config.dragKill
                    "openAospSettings" -> config.openAospSettings
                    "disablePreLaunch" -> config.disablePreLaunch
                    else -> false
                }
            }
            findClassOrNull("com.android.server.pm.IPackageManagerBase")?.deoptimize("hasSystemFeature")
            findClassOrNull("com.android.server.pm.PackageManagerNative")?.deoptimize("hasSystemFeature")
        }.onFailure { logE("MiuiHome native config bridge", it) }
        runCatching {
            findClass("com.android.server.am.ActivityManagerService").hookBefore(
                "preStartProcess", String::class.java, Intent::class.java, Integer.TYPE
            ) { param ->
                val intent = param.args[1] as? Intent
                if (param.args[0] == COMMAND_PACKAGE && intent?.action == COMMAND) {
                    // Consume the marker even when its sender is rejected. If this
                    // module unloads, the marker names no installed application.
                    param.result = null
                    if (isLauncherCaller()) {
                        val request = readRequest(intent, Binder.getCallingPid())
                        if (request != null) handler?.post { execute(request) }
                    }
                } else if (config.disablePreLaunch && isLauncherCaller()) {
                    param.result = null
                }
            }
            ready = true
        }.onFailure { logE("MiuiHome preStartProcess bridge", it) }
        logI("MiuiHome HYOS system bridge ready")
    }

    private fun isLauncherCaller(): Boolean = runCatching {
        val uid = Binder.getCallingUid()
        uid % 100000 >= 10000 && systemContext.packageManager.call(
            "getPackageUidAsUser", LAUNCHER, uid / 100000
        ) == uid
    }.getOrDefault(false)

    private data class Request(
        val pid: Int, val operation: Int, val generation: Long, val taskId: Int,
        val userId: Int, val packageName: String, val durationMs: Long
    )

    private fun readRequest(intent: Intent, pid: Int): Request? = runCatching {
        val op = intent.getIntExtra("operation", 0)
        val gen = intent.getLongExtra("generation", 0)
        val task = intent.getIntExtra("taskId", -1)
        val user = intent.getIntExtra("userId", -1)
        val pkg = intent.getStringExtra("package") ?: return null
        val duration = intent.getLongExtra("durationMs", 0)
        if (op !in 1..3 || gen <= 0 || task < 0 || user < 0 ||
            pkg.length !in 1..255 || !pkg.matches(Regex("[A-Za-z0-9_.]+"))) return null
        Request(pid, op, gen, task, user, pkg, duration)
    }.getOrNull()

    private fun isCurrentTask(request: Request): Boolean {
        val atm = findClass("android.app.IActivityTaskManager\$Stub")
            .callS("asInterface", ServiceManager.getService("activity_task")) ?: return false
        val tasks = atm.call("getRecentTasks", 200, 1, request.userId)
            ?.call("getList") as? List<*> ?: return false
        return tasks.any { task ->
            task != null && task.getObjAsN<Int>("taskId") == request.taskId &&
                (task.getObjAsN<Int>("userId") ?: request.userId) == request.userId &&
                listOf(task.getObjAsN<ComponentName>("topActivity"),
                    task.getObjAsN<ComponentName>("baseActivity"))
                    .any { it?.packageName == request.packageName }
        }
    }

    private fun execute(request: Request) = runCatching {
        // Handler execution has system_server's identity, not the launcher's Binder identity.
        val key = "${request.pid}:${request.generation}:${request.operation}"
        if (seen.containsKey(key)) return@runCatching
        seen[key] = Unit
        while (seen.size > 128) seen.remove(seen.keys.first())
        when (request.operation) {
            1 -> {
                if (!config.dragKill || request.durationMs < 700) return@runCatching
                // The original swipe has removed the task by this point; the identity
                // was copied at drag start, then committed by the exact killProcess.
                IActivityManager.Stub.asInterface(ServiceManager.getService("activity"))
                    .forceStopPackage(request.packageName, request.userId)
                Toast.makeText(systemContext, "killed ${request.packageName}", Toast.LENGTH_SHORT).show()
            }
            2 -> {
                if (!config.openAospSettings || request.durationMs < 500 ||
                    !isCurrentTask(request)) return@runCatching
                val user = findClass("android.os.UserHandle")
                    .getDeclaredConstructor(Integer.TYPE).newInstance(request.userId)
                val context = systemContext.call("createContextAsUser", user, 0) as Context
                TaskStackBuilder.create(context).addNextIntentWithParentStack(
                    Intent().setComponent(ComponentName("com.android.settings", "com.android.settings.SubSettings"))
                        .setData(Uri.parse("package:${request.packageName}"))
                        .putExtra(":settings:show_fragment",
                            "com.android.settings.applications.appinfo.AppInfoDashboardFragment")
                ).startActivities()
            }
            3 -> {
                if (config.dragKill && request.durationMs >= 700 && isCurrentTask(request))
                    Toast.makeText(systemContext, "停止进程", Toast.LENGTH_SHORT).show()
            }
        }
        logI("MiuiHome native command ${request.operation}: ${request.packageName}/${request.userId}")
    }.onFailure { logE("MiuiHome native command", it) }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            runCatching {
                val credential = intent.getParcelableExtraCompat("EXTRA_CREDENTIAL", PendingIntent::class.java)
                if (credential?.creatorPackage != BuildConfig.APPLICATION_ID) return
                val bytes = intent.getByteArrayExtra("EXTRA_CONFIG") ?: return
                val updated = MiuiHomeConfig.parseFrom(bytes)
                val output = configFile.startWrite()
                try {
                    output.write(bytes)
                    configFile.finishWrite(output)
                } catch (error: Throwable) {
                    configFile.failWrite(output)
                    throw error
                }
                config = updated
                logI("MiuiHome native config updated: $updated")
            }.onFailure { logE("MiuiHome native config", it) }
        }
    }

    override fun onUnhook(): Boolean {
        ready = false
        broadcasts?.unregister(receiver)
        broadcasts?.stop()
        handler?.removeCallbacksAndMessages(null)
        worker?.quitSafely()
        broadcasts = null
        worker = null
        handler = null
        return true
    }
}
