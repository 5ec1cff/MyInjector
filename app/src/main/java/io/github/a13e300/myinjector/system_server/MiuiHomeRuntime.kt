package io.github.a13e300.myinjector.system_server

import android.app.ActivityThread
import android.app.IActivityManager
import android.app.TaskStackBuilder
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Binder
import android.os.Handler
import android.os.HandlerThread
import android.os.ServiceManager
import android.widget.Toast
import io.github.a13e300.myinjector.MiuiHomeConfig
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.logI

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
    private val remoteConfig = io.github.a13e300.myinjector.config.RemoteConfig(
        "miuihome", io.github.a13e300.myinjector.config.HookConfigs::miuiHome,
    ) { updated ->
        config = updated
    }
    private var worker: HandlerThread? = null
    private var handler: Handler? = null
    private val seen = LinkedHashMap<String, Unit>() // accessed only on worker
    private val systemContext: Context
        get() = ActivityThread.currentActivityThread().systemContext

    override fun onHook() {
        if (findClassOrNull("com.android.server.am.ProcessListStub") == null) return
        worker = HandlerThread("MyInjector-MiuiHome").also { it.start() }
        handler = Handler(worker!!.looper)
        remoteConfig.start()
        runCatching {
            findClass("com.android.server.am.ActivityManagerService").hookBefore(
                "preStartProcess", String::class.java, Intent::class.java, Integer.TYPE
            ) { param ->
                val intent = param.args[1] as? Intent
                if (param.args[0] == COMMAND_PACKAGE && intent?.action == COMMAND) {
                    // Consume the marker even when its sender is rejected. If this
                    // module unloads, the marker names no installed application.
                    param.result = null
                    if (isLauncherCaller() && intent.getIntExtra("protocol", 0) == 1) {
                        val request = readRequest(intent, Binder.getCallingPid())
                        if (request != null) handler?.post { execute(request) }
                    }
                } else if (config.disablePreLaunch && isLauncherCaller()) {
                    param.result = null
                }
            }
            ready = true
        }.onFailure { logE("MiuiHome preStartProcess bridge", it) }
        logI("MiuiHome HYOS system bridge initialized: ready=$ready")
    }

    private fun androidUser(id: Int): Any = findClass("android.os.UserHandle")
        .getDeclaredConstructor(Integer.TYPE).newInstance(id)

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
        if (!ready) return@runCatching
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
                val user = androidUser(request.userId)
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

    override fun onUnhook(): Boolean {
        remoteConfig.close()
        ready = false
        handler?.removeCallbacksAndMessages(null)
        worker?.quitSafely()
        worker = null
        handler = null
        return true
    }
}
