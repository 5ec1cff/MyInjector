package io.github.a13e300.myinjector.systemui

import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.VelocityTracker
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.bridge.Xposed
import io.github.a13e300.myinjector.logD
import io.github.a13e300.myinjector.logE
import java.util.IdentityHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 只扩展原生允许的反向切换，页面、手势分发和动画生命周期仍由 SystemUI 管理。 */
internal class ShadeLoopHook(private val enabled: () -> Boolean) : IHook() {
    private class Touch(val event: MotionEvent, val previous: Touch?)
    private class Session(
        val animator: Any,
        val notification: Any,
        val control: Any,
        val tracker: VelocityTracker,
        val geometry: ShadeLoopGeometry,
    )

    // SystemUI 的触摸、动画、资源更新均在 UI 线程；跨线程卸载也派发到该线程。
    private val sessions = IdentityHashMap<Any, Session>()
    private val touch = ThreadLocal<Touch?>()
    private val hooks = mutableListOf<Unhook>()
    private var failed = false
    private var closed = false
    private var recovering = false

    override fun onHook() {
        val controller = findClassOrNull("com.miui.systemui.shade.ShadeSwitchControllerImpl") ?: return
        val window = findClass("com.android.systemui.shade.NotificationShadeWindowView")
        val animator = Xposed.findField(controller, "progressAnimator").type
        val wrapper = Xposed.findField(controller, "notification").type
        val floatType = Float::class.javaPrimitiveType!!
        val boolType = Boolean::class.javaPrimitiveType!!
        // 安装前确认整套结构，避免在旧版本上留下半套 Hook。
        listOf("switching", "dragging", "switchIntercepted", "fromCtrl", "toCtrl", "initX", "initY",
            "touchSlop", "slopMultiplier", "switchDistance", "controlCenter", "velocityTracker\$delegate",
            "progressAnimating").forEach { Xposed.findField(controller, it) }
        listOf("triggerShifting", "slideAnimating").forEach { Xposed.findField(wrapper, it) }
        Xposed.findField(animator, "this\$0")
        Xposed.findField(animator, "_progress")
        Xposed.findField(animator, "anim")
        val progress = Xposed.findMethodExact(animator, "setProgress", arrayOf(floatType))
        Xposed.findMethodExact(animator, "setProgress", arrayOf(floatType, boolType))
        Xposed.findMethodExact(wrapper, "setTriggerAppeared", arrayOf(boolType, boolType))
        Xposed.findMethodExact(wrapper, "updateTransX", emptyArray())
        val switching = Xposed.findMethodExact(controller, "setSwitching", arrayOf(boolType))
        Xposed.findMethodExact(controller, "getControlCenterExpanded", emptyArray())
        Xposed.findMethodExact(controller, "getNotificationExpanded", emptyArray())
        val resources = controller.declaredMethods.single { it.name.startsWith("updateResources") && it.parameterCount == 0 }
        val onTouch = Xposed.findMethodExact(window, "onTouchEvent", arrayOf(MotionEvent::class.java))

        try {
            hooks += progress.hookBefore { param ->
                safely { beginIfOutward(param.thisObject) }
            }
            // 原生松手逻辑按 RTL 判断速度方向。反向距离需要同一次触摸中的速度同步反号。
            // 只匹配控制器私有 tracker，不修改 MotionEvent，也不影响其他控件的速度读数。
            hooks += VelocityTracker::class.java.getDeclaredMethod("getXVelocity").hookAfter { param ->
                if (param.throwable == null && touch.get() != null && sessions.values.any { it.tracker === param.thisObject }) {
                    param.result = -(param.result as Float)
                }
            }
            hooks += switching.hookAfter { param ->
                if (param.args[0] == false) safely { restore(param.thisObject) }
            }
            hooks += resources.hookBefore { param ->
                safely { finish(param.thisObject) }
            }
            // 最后才启用触摸上下文，避免热重载安装过程中出现半套方向处理。
            hooks += onTouch.hook(
                before = { param ->
                    touch.set(Touch(param.args[0] as MotionEvent, touch.get()))
                },
                after = { param ->
                    try {
                        if (param.throwable != null) safely { finishAll() }
                    } finally {
                        val previous = touch.get()?.previous
                        if (previous == null) touch.remove() else touch.set(previous)
                    }
                },
            )
            // 保证触摸原方法仍调用已 Hook 的 setProgress / getXVelocity，避免 ART 内联绕过。
            onTouch.deoptimize()
            logD("notification/control-center loop hooks installed")
        } catch (error: Throwable) {
            hooks.asReversed().forEach { it.unhook() }
            hooks.clear()
            throw error
        }
    }

    private fun beginIfOutward(animator: Any) {
        if (closed || failed || !enabled()) return
        val event = touch.get()?.event ?: return
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return
        val controller = animator.getObj("this\$0") ?: return
        if (sessions.containsKey(controller) || controller.getObjAs<Boolean>("switching") ||
            !controller.getObjAs<Boolean>("dragging") || !controller.getObjAs<Boolean>("switchIntercepted")) return

        // 只会在原生通过 isSwitchable、父级接管及横向阈值检查后，初始化 p=0/1 的首帧进入。
        val notification = controller.getObj("notification")!!
        val control = controller.getObj("controlCenter")!!
        val geometry = ShadeLoopGeometry(controller.getObjAs("switchDistance"),
            notification.getObjAs("triggerShifting"), control.getObjAs("triggerShifting"))
        var slop = controller.getObjAs<Float>("touchSlop")
        if (event.classification == MotionEvent.CLASSIFICATION_AMBIGUOUS_GESTURE) {
            slop *= controller.getObjAs<Float>("slopMultiplier")
        }
        val fromControl = controller.getObjAs<Boolean>("fromCtrl")
        if (!geometry.isOutward(fromControl,
                event.rawX - controller.getObjAs<Float>("initX"),
                event.rawY - controller.getObjAs<Float>("initY"), slop)) return
        val controlExpanded = controller.call("getControlCenterExpanded") as Boolean
        val notificationExpanded = controller.call("getNotificationExpanded") as Boolean
        if (controlExpanded == notificationExpanded || controlExpanded != fromControl) return
        val tracker = controller.getObj("velocityTracker\$delegate").call("getValue") as VelocityTracker
        sessions[controller] = Session(animator, notification, control, tracker, geometry)
        applyGeometry(controller, geometry.mirrored())
    }

    private fun applyGeometry(controller: Any, geometry: ShadeLoopGeometry) {
        val session = sessions.getValue(controller)
        controller.setObj("switchDistance", geometry.distance)
        session.notification.setObj("triggerShifting", geometry.notificationShift)
        session.control.setObj("triggerShifting", geometry.controlShift)
    }

    private fun restore(controller: Any) {
        if (!sessions.containsKey(controller)) return
        val session = sessions.getValue(controller)
        applyGeometry(controller, session.geometry)
        sessions.remove(controller)
        session.notification.call("updateTransX")
        session.control.call("updateTransX")
    }

    /** 资源重算、错误或卸载时立即收束到最近端点，再交给原生关闭另一页并恢复几何参数。 */
    private fun finish(controller: Any) {
        val session = sessions[controller] ?: return
        try {
            controller.setObj("dragging", true)
            session.animator.getObj("anim").call("cancel")
            val toControl = session.animator.getObjAs<Float>("_progress") < 0.5f
            controller.setObj("toCtrl", toControl)
            session.animator.call("setProgress", if (toControl) 0f else 1f, false)
            session.notification.call("setTriggerAppeared", !toControl, false)
            session.control.call("setTriggerAppeared", toControl, false)
            controller.setObj("dragging", false)
            controller.setObj("progressAnimating", false)
            session.notification.setObj("slideAnimating", false)
            controller.call("setSwitching", false)
        } finally {
            controller.setObj("dragging", false)
            restore(controller)
        }
    }

    private fun finishAll() {
        if (recovering) return
        recovering = true
        try {
            sessions.keys.toList().forEach { controller ->
                try {
                    finish(controller)
                } catch (error: Throwable) {
                    logE("restore shade loop", error)
                }
            }
        } finally {
            recovering = false
        }
    }

    private inline fun safely(block: () -> Unit) {
        try {
            block()
        } catch (error: Throwable) {
            if (!failed) logE("shade loop disabled after error", error)
            failed = true
            finishAll()
        }
    }

    fun close(): Boolean {
        fun cleanup() {
            closed = true
            finishAll()
            check(sessions.isEmpty()) { "shade loop geometry has not been restored" }
            hooks.asReversed().forEach { it.unhook() }
            hooks.clear()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            return try {
                cleanup()
                true
            } catch (error: Throwable) {
                logE("close shade loop", error)
                false
            }
        }
        val done = CountDownLatch(1)
        var success = false
        Handler(Looper.getMainLooper()).post {
            try {
                cleanup()
                success = true
            } catch (error: Throwable) {
                logE("close shade loop", error)
            } finally {
                done.countDown()
            }
        }
        return done.await(5, TimeUnit.SECONDS) && success
    }
}
