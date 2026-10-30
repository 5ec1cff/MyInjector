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
    private class Appearance(val previous: Appearance?, var mirrored: Boolean = false)
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
    private val appearance = ThreadLocal<Appearance?>()
    private val controlAnimators = mutableSetOf<Class<*>>()
    private val hooks = mutableListOf<Unhook>()
    @Volatile private var installed = false
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
        Xposed.findField(wrapper, "this\$0")
        Xposed.findField(controller, "listeners")
        Xposed.findField(animator, "this\$0")
        Xposed.findField(animator, "_progress")
        Xposed.findField(animator, "anim")
        val progress = Xposed.findMethodExact(animator, "setProgress", arrayOf(floatType))
        Xposed.findMethodExact(animator, "setProgress", arrayOf(floatType, boolType))
        val triggerAppeared = Xposed.findMethodExact(wrapper, "setTriggerAppeared", arrayOf(boolType, boolType))
        Xposed.findMethodExact(wrapper, "updateTransX", emptyArray())
        val notificationValues = findClass("com.android.systemui.shade.MiuiPanelAnimValueObjectImpl")
        val slideNotification = notificationValues.declaredMethods.single {
            it.name == "slideNotificationCenter" && it.parameterTypes.take(4) ==
                listOf(Int::class.javaPrimitiveType, boolType, Int::class.javaPrimitiveType, boolType) &&
                it.parameterCount == 5
        }
        val notificationAppearance = Xposed.findMethodExact(
            findClass("com.android.systemui.shade.NotificationPanelViewControllerInjector"),
            "onNotificationAppearChanged", arrayOf(boolType, boolType))
        val switching = Xposed.findMethodExact(controller, "setSwitching", arrayOf(boolType))
        Xposed.findMethodExact(controller, "getControlCenterExpanded", emptyArray())
        Xposed.findMethodExact(controller, "getNotificationExpanded", emptyArray())
        Xposed.findMethodExact(controller, "isSwitchable", emptyArray())
        val resources = controller.declaredMethods.single { it.name.startsWith("updateResources") && it.parameterCount == 0 }
        val onTouch = Xposed.findMethodExact(window, "onTouchEvent", arrayOf(MotionEvent::class.java))
        val intercept = Xposed.findMethodExact(window, "onInterceptTouchEvent", arrayOf(MotionEvent::class.java))
        val interactionHandler = Xposed.findField(window, "mInteractionEventHandler").type
        val windowController = Xposed.findField(interactionHandler, "this\$0").type
        val touchHandler = Xposed.findField(windowController, "mShadeTouchHandler").type
        check(Xposed.findField(touchHandler, "shadeSwitchControllerImpl").type == controller)

        try {
            hooks += slideNotification.hookBefore { param ->
                param.args[2] = ShadeLoopGeometry.animationDirection(param.args[2] as Int,
                    appearance.get()?.mirrored == true)
            }
            hooks += triggerAppeared.hook(
                before = { param ->
                    val scope = Appearance(appearance.get())
                    appearance.set(scope)
                    safely {
                        val owner = param.thisObject.getObj("this\$0")
                        if (sessions.containsKey(owner)) {
                            prepareControlAnimations(owner!!)
                            scope.mirrored = sessions.containsKey(owner)
                        }
                    }
                },
                after = {
                    val previous = appearance.get()?.previous
                    if (previous == null) appearance.remove() else appearance.set(previous)
                },
            )
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
            hooks += intercept.hookAfter { param ->
                if (param.throwable == null && param.result == true) {
                    safely { finishBeforeNextDrag(param.thisObject, param.args[0] as MotionEvent) }
                }
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
            notificationAppearance.deoptimize()
            switching.deoptimize()
            controller.deoptimize("performProgressChanged")
            installed = true
            logD("notification/control-center loop hooks installed")
        } catch (error: Throwable) {
            hooks.asReversed().forEach { it.unhook() }
            hooks.clear()
            throw error
        }
    }

    /** 插件有独立 ClassLoader；在通知回调前从原生监听器中找到实际实例，兼顾已加载及重建的插件。 */
    private fun prepareControlAnimations(controller: Any) {
        val listeners = controller.getObjAs<List<Any>>("listeners")
        for (listener in listeners) {
            val type = listener.javaClass
            if (type.name != "miui.systemui.controlcenter.panel.main.anim.SpreadRowsAnimator" ||
                type in controlAnimators) continue
            val boolType = Boolean::class.javaPrimitiveType!!
            val slide = Xposed.findMethodExact(type, "slideControlCenter",
                arrayOf(boolType, Int::class.javaPrimitiveType!!, boolType))
            val callback = Xposed.findMethodExact(type, "onControlCenterAppearChanged", arrayOf(boolType, boolType))
            hooks += slide.hookBefore { param ->
                param.args[1] = ShadeLoopGeometry.animationDirection(param.args[1] as Int,
                    appearance.get()?.mirrored == true)
            }
            callback.deoptimize()
            controlAnimators += type
        }
    }

    /** 原生接管新手势后，向目标页外续划需要先收尾上一段，否则会沿旧方向进入阻尼区。 */
    private fun finishBeforeNextDrag(window: Any, event: MotionEvent) {
        if (!installed || closed || failed || !enabled() || event.actionMasked != MotionEvent.ACTION_MOVE) return
        val controller = window.getObj("mInteractionEventHandler").getObj("this\$0")
            .getObj("mShadeTouchHandler").getObj("shadeSwitchControllerImpl") ?: return
        if (!controller.getObjAs<Boolean>("switchIntercepted") ||
            !controller.getObjAs<Boolean>("switching") || controller.getObjAs<Boolean>("dragging")) return
        if (controller.call("isSwitchable") != true) return
        val targetControl = controller.getObjAs<Boolean>("toCtrl")
        val geometry = geometry(controller)
        if (geometry.shouldFinishBeforeNextDrag(true, false, targetControl,
                event.rawX - controller.getObjAs<Float>("initX"),
                event.rawY - controller.getObjAs<Float>("initY"), slop(controller, event))) {
            finishAt(controller, targetControl)
        }
    }

    private fun geometry(controller: Any) = ShadeLoopGeometry(controller.getObjAs("switchDistance"),
        controller.getObj("notification").getObjAs("triggerShifting"),
        controller.getObj("controlCenter").getObjAs("triggerShifting"))

    private fun slop(controller: Any, event: MotionEvent): Float {
        val slop = controller.getObjAs<Float>("touchSlop")
        return if (event.classification == MotionEvent.CLASSIFICATION_AMBIGUOUS_GESTURE) {
            slop * controller.getObjAs<Float>("slopMultiplier")
        } else slop
    }

    private fun beginIfOutward(animator: Any) {
        if (!installed || closed || failed || !enabled()) return
        val event = touch.get()?.event ?: return
        if (event.actionMasked != MotionEvent.ACTION_MOVE) return
        val controller = animator.getObj("this\$0") ?: return
        if (sessions.containsKey(controller) || controller.getObjAs<Boolean>("switching") ||
            !controller.getObjAs<Boolean>("dragging") || !controller.getObjAs<Boolean>("switchIntercepted")) return

        // 只会在原生通过 isSwitchable、父级接管及横向阈值检查后，初始化 p=0/1 的首帧进入。
        val notification = controller.getObj("notification")!!
        val control = controller.getObj("controlCenter")!!
        val geometry = geometry(controller)
        val fromControl = controller.getObjAs<Boolean>("fromCtrl")
        if (!geometry.isOutward(fromControl,
                event.rawX - controller.getObjAs<Float>("initX"),
                event.rawY - controller.getObjAs<Float>("initY"), slop(controller, event))) return
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
        finishAt(controller, session.animator.getObjAs<Float>("_progress") < 0.5f)
    }

    private fun finishAt(controller: Any, toControl: Boolean) {
        val animator = controller.getObj("progressAnimator")!!
        val notification = controller.getObj("notification")!!
        val control = controller.getObj("controlCenter")!!
        try {
            controller.setObj("dragging", true)
            animator.getObj("anim").call("cancel")
            controller.setObj("toCtrl", toControl)
            animator.call("setProgress", if (toControl) 0f else 1f, false)
            notification.call("setTriggerAppeared", !toControl, false)
            control.call("setTriggerAppeared", toControl, false)
            controller.setObj("dragging", false)
            controller.setObj("progressAnimating", false)
            notification.setObj("slideAnimating", false)
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
            installed = false
            closed = true
            finishAll()
            check(sessions.isEmpty()) { "shade loop geometry has not been restored" }
            hooks.asReversed().forEach { it.unhook() }
            hooks.clear()
            controlAnimators.clear()
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
