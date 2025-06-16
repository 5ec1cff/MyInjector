package io.github.a13e300.myinjector.system_server

import android.app.ActivityThread
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.ServiceManager
import io.github.a13e300.myinjector.BuildConfig
import io.github.a13e300.myinjector.arch.getParcelableExtraCompat
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.logI

fun isCallerTrusted(intent: Intent): Boolean {
    val pendingIntent =
        intent.getParcelableExtraCompat("EXTRA_CREDENTIAL", PendingIntent::class.java)
    val caller = pendingIntent?.creatorPackage
    return caller == BuildConfig.APPLICATION_ID || caller == "android" || caller == "com.android.shell"
}

class BroadcastManager(name: String) {
    private val thread: HandlerThread = HandlerThread(name)
    private val handler: Handler

    init {
        thread.start()
        handler = Handler(thread.looper)
    }

    fun register(intentFilter: IntentFilter, receiver: BroadcastReceiver) {
        val registerTask = object : Runnable {
            override fun run() {
                try {
                    if (ServiceManager.getService("activity") == null) {
                        handler.postDelayed(this, 1000)
                        return
                    }
                    val ctx = ActivityThread.currentActivityThread().systemContext as Context
                    val flags =
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Context.RECEIVER_EXPORTED else 0
                    ctx.registerReceiver(
                        receiver,
                        intentFilter,
                        null,
                        handler,
                        flags
                    )
                    logI("BroadcastManager ${thread.name}: registered $receiver")
                } catch (t: Throwable) {
                    logE("BroadcastManager ${thread.name}: register $receiver failed", t)
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(registerTask)
    }

    fun unregister(receiver: BroadcastReceiver) {
        runCatching {
            val ctx = ActivityThread.currentActivityThread().systemContext as Context
            ctx.unregisterReceiver(receiver)
        }.onFailure {
            logE("BroadcastManager ${thread.name}: unregister $receiver failed", it)
        }
    }

    fun stop() {
        thread.quitSafely()
    }
}