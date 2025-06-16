package io.github.a13e300.myinjector.system_server

import android.app.ActivityThread
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import io.github.a13e300.myinjector.BuildConfig
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.arch.HotLoadClassLoader
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.bridge.HotLoadHook
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.logI
import io.github.a13e300.myinjector.logW
import io.github.a13e300.myinjector.system_server.ResultReceiver.Companion.writeResult
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

object SystemServerHookLoader : IHook() {
    private val broadcastManager = BroadcastManager("MyInjector-SystemServerHookLoader")
    private val recycledHook = mutableListOf<WeakReference<Pair<HotLoadHook?, String?>>>()
    private var currentHook: HotLoadHook? = null
    private var currentPath: String? = null


    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            intent ?: return
            if (!isCallerTrusted(intent)) {
                logE("invalid caller!")
                return
            }

            thread {
                var resultReceiver: IBinder? = intent.extras?.getBinder("EXTRA_RECEIVER")
                fun reply(code: Int, data: Bundle? = null) {
                    resultReceiver?.writeResult(code, data)
                    resultReceiver = null
                }
                runCatching {
                    val action = intent.getStringExtra("EXTRA_ACTION")
                    when (action) {
                        "reload" -> {
                            val ctx =
                                ActivityThread.currentActivityThread().systemContext as Context
                            val appInfo =
                                ctx.packageManager.getApplicationInfo(BuildConfig.APPLICATION_ID, 0)
                            reload(appInfo.sourceDir, intent.getBooleanExtra("force", false))
                            reply(1)
                        }

                        "unload" -> {
                            reload(null, true)
                        }

                        "reportOldHook" -> {
                            recycledHook.removeIf { it.get() == null }
                            reply(0, Bundle().apply {
                                putString("hooks", recycledHook.joinToString(",") {
                                    val o = it.get()
                                    "${o?.first}:${o?.second}"
                                })
                            })
                        }

                        "needUpdate" -> {
                            val ctx =
                                ActivityThread.currentActivityThread().systemContext as Context
                            val appInfo: ApplicationInfo
                            try {
                                appInfo = ctx.packageManager.getApplicationInfo(
                                    BuildConfig.APPLICATION_ID,
                                    0
                                )
                            } catch (_: PackageManager.NameNotFoundException) {
                                reply(0)
                                return@runCatching
                            }
                            reply(if (appInfo.sourceDir != currentPath) 1 else 0)
                        }

                        "gc" -> {
                            logI("gc requested by remote")
                            System.gc()
                        }
                    }
                }.onFailure {
                    logE("onReceive", it)
                    reply(-1)
                }
                reply(0)
            }
        }
    }

    @Suppress("DEPRECATION")
    @Synchronized
    private fun reload(sourceDir: String?, force: Boolean) {
        recycledHook.removeIf { it.get() == null }
        if (recycledHook.isNotEmpty()) {
            val oldHookStr = recycledHook.joinToString(",") {
                val o = it.get()
                "${o?.first}:${o?.second}"
            }
            logW("old modules are not unloaded: $oldHookStr")
        }
        if (currentPath == sourceDir && currentPath != null && !force) {
            logI("not reloading since source dir not changed")
            return
        }
        currentHook?.let { old ->
            runCatching {
                old.onUnload()
            }.onFailure {
                logE("unload $old failed", it)
            }
            recycledHook.add(WeakReference(Pair(currentHook, currentPath)))
            logI("unload $old $currentPath")
        }
        currentHook = null
        currentPath = null
        sourceDir ?: return
        runCatching {
            val cl = HotLoadClassLoader(sourceDir, javaClass.classLoader)
            val hook =
                cl.loadClass("io.github.a13e300.myinjector.system_server.SystemServerHandler")
                    .newInstance() as HotLoadHook
            currentHook = hook
            currentPath = sourceDir
            hook.onLoad(loadPackageParam)
            logI("loaded $hook from $sourceDir")
        }.onFailure {
            logE("load $sourceDir failed", it)
            runCatching {
                currentHook?.onUnload()
            }.onFailure { e2 ->
                logE("unload when load fail $sourceDir failed", e2)
            }
            recycledHook.add(WeakReference(Pair(currentHook, currentPath)))
            currentHook = null
            currentPath = null
        }
    }

    override fun onHook() {
        reload(Entry.modulePath, true)
        broadcastManager.register(
            IntentFilter("io.github.a13e300.myinjector.SYSTEM_SERVER_ENTRY"),
            receiver
        )
    }
}
