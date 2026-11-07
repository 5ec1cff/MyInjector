package io.github.a13e300.myinjector.telegram

import android.os.Handler
import android.os.HandlerThread
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAll
import java.lang.reflect.Field
import org.luckypray.dexkit.wrap.DexField
import io.github.a13e300.myinjector.arch.toObfsInfo
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch

class StickerLoadGuard : IHook() {
    private lateinit var operationField: Field
    private lateinit var stateField: Field
    private lateinit var latchField: Field
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
                        val loadOp = operationField.get(k)
                        val state = stateField.getInt(loadOp)
                        if (state == 5) { // stateCancelling
                            k.call("cancel")
                            // logD("cancelled ${k.hashCode()} which stuck in state = 5")
                            true
                        } else {
                            false
                        }
                    }.getOrDefault(false)
                    if (!cancel) (latchField.get(k) as? CountDownLatch)?.countDown()
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
        val creator = TelegramHandler.creator
        val found by lazy { findStickerStream(creator.bridge) }
        fun field(key: String): Field {
            val member = creator.create("StickerStream.$key") { bridge -> bridge.getFieldData(found.getValue(key))!!.toObfsInfo() }
            return DexField(member.descriptor).getFieldInstance(classLoader).apply { isAccessible = true }
        }
        operationField = field("operation")
        stateField = field("state")
        latchField = field("latch")
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
