package io.github.a13e300.myinjector.telegram

import android.os.Handler
import android.os.HandlerThread
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAll
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch

class StickerLoadGuard : IHook() {
    private val objects = IdentityHashMap<Any, Int>()
    private val lock = Object()


    private val handler: Handler

    init {
        val handlerThread = HandlerThread("StickerLoadGuard")
        handlerThread.start()
        handler = Handler(handlerThread.looper)
    }

    private val runnable = Runnable {
        check()
    }

    private fun check() {
        synchronized(lock) {
            objects.keys.forEach { k ->
                runCatching {
                    val cancel = runCatching {
                        val loadOp = k.getObj("loadOperation")
                        val state = loadOp.getObjAs<Int>("state")
                        if (state == 5) { // stateCancelling
                            k.call("cancel")
                            // logD("cancelled ${k.hashCode()} which stuck in state = 5")
                            true
                        } else {
                            false
                        }
                    }.getOrDefault(false)
                    if (!cancel) k.getObjAsN<CountDownLatch>("countDownLatch")?.countDown()
                }
            }
            if (objects.isNotEmpty()) {
                // logD("checked ${objects.size}")
                handler.postDelayed(runnable, 1000)
            }
        }
    }

    private fun addToMonitor(obj: Any) {
        synchronized(lock) {
            val previousEmpty = objects.isEmpty()
            objects[obj] = (objects[obj] ?: 0) + 1
            if (previousEmpty) {
                handler.postDelayed(runnable, 1000)
            }
        }
    }

    private fun removeFromMonitor(obj: Any) {
        synchronized(lock) {
            val v = objects[obj]
            if (v == 1) {
                objects.remove(obj)
            } else if (v != null) {
                objects[obj] = v - 1
            }
            if (objects.isEmpty()) {
                handler.removeCallbacks(runnable)
            }
        }
    }

    override fun onHook() {
        findClass("org.telegram.messenger.AnimatedFileDrawableStream").hookAll(
            "read",
            before = { param ->
                addToMonitor(param.thisObject)
            },
            after = { param ->
                removeFromMonitor(param.thisObject)
            }
        )
    }
}
