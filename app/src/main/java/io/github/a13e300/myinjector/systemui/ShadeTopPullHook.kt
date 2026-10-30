package io.github.a13e300.myinjector.systemui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowInsets
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.bridge.Xposed
import io.github.a13e300.myinjector.logD
import io.github.a13e300.myinjector.logE
import java.util.WeakHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 复用系统热区和 switchTo…，仅在已展开面板的顶部补充下拉切页。 */
internal class ShadeTopPullHook(private val enabled: () -> Boolean) : IHook() {
    private class Gesture(
        val controller: Any,
        val recognizer: TopPullGesture,
        val downTime: Long,
        val width: Int,
        val height: Int,
        val slop: Float,
        val slopMultiplier: Float,
        var claimed: Boolean = false,
    )

    // 与原生触摸同在 UI 线程。接管后先向原路径发送 CANCEL，再消费到 UP/CANCEL。
    private val gestures = WeakHashMap<View, Gesture>()
    private var hook: Unhook? = null
    private var cancelling = false
    private var closed = false
    private var failed = false

    override fun onHook() {
        val controller = findClassOrNull("com.miui.systemui.shade.ShadeSwitchControllerImpl") ?: return
        val window = findClass("com.android.systemui.shade.NotificationShadeWindowView")
        val interaction = Xposed.findField(window, "mInteractionEventHandler").type
        val windowController = Xposed.findField(interaction, "this\$0").type
        val touchHandler = Xposed.findField(windowController, "mShadeTouchHandler").type
        check(Xposed.findField(touchHandler, "shadeSwitchControllerImpl").type == controller)
        val controlEvents = Xposed.findField(touchHandler, "controlCenterEventHandlerImpl").type
        listOf("switching", "dragging").forEach { Xposed.findField(controller, it) }
        listOf("getControlCenterExpanded", "getNotificationExpanded", "isSwitchable",
            "switchToControlCenter", "switchToNotification").forEach {
            Xposed.findMethodExact(controller, it, emptyArray())
        }
        Xposed.findMethodExact(controlEvents, "calculateCtrlHotZone", arrayOf(
            Float::class.javaPrimitiveType!!, Float::class.javaPrimitiveType!!, Boolean::class.javaPrimitiveType!!))
        listOf("isFlipDevice", "container", "controlCenterSettingsController").forEach {
            Xposed.findField(controlEvents, it)
        }
        listOf("mExpandAnimationRunning", "mIsOcclusionTransitionRunning", "mStatusBarStateController",
            "mPrimaryBouncerInteractor", "mService").forEach { Xposed.findField(windowController, it) }
        val configs = findClass("com.miui.utils.configs.MiuiConfigs")
        Xposed.findMethodExact(configs, "isTinyScreen", arrayOf(Context::class.java, Boolean::class.javaPrimitiveType!!))
        Xposed.findMethodExact(configs, "isCutoutRight", arrayOf(Context::class.java))

        hook = Xposed.findMethodExact(window, "dispatchTouchEvent", arrayOf(MotionEvent::class.java)).hook(
            before = { param ->
                if (!cancelling) {
                    val view = param.thisObject as View
                    val event = param.args[0] as MotionEvent
                    try {
                        if (handle(view, event, configs)) param.result = true
                    } catch (error: Throwable) {
                        if (!failed) logE("shade top pull disabled after error", error)
                        failed = true
                        // 已发送 CANCEL 的流必须继续消费，不能把无 DOWN 的剩余事件交给新页面。
                        if (gestures[view]?.claimed == true) param.result = true
                        else gestures.remove(view)
                    }
                }
            },
            after = { param ->
                if (!cancelling) {
                    val view = param.thisObject as View
                    val event = param.args[0] as MotionEvent
                    if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL ||
                        (event.actionMasked == MotionEvent.ACTION_DOWN && (param.throwable != null || param.result != true))) {
                        gestures.remove(view)
                    }
                    unhookWhenIdle()
                }
            },
        )
        logD("expanded shade top pull hook installed")
    }

    private fun handle(view: View, event: MotionEvent, configs: Class<*>): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            gestures.remove(view)
            if (!closed && !failed && enabled() && event.pointerCount == 1) {
                start(view, event, configs)?.let { gestures[view] = it }
            }
            return false
        }
        val gesture = gestures[view] ?: return false
        if (event.downTime != gesture.downTime) {
            gestures.remove(view)
            return false
        }
        if (gesture.claimed) return true
        if (closed || failed || !enabled() || event.pointerCount != 1 ||
            view.width != gesture.width || view.height != gesture.height ||
            event.actionMasked == MotionEvent.ACTION_POINTER_DOWN) {
            gestures.remove(view)
            return false
        }
        if (event.actionMasked != MotionEvent.ACTION_MOVE && event.actionMasked != MotionEvent.ACTION_UP) return false
        val slop = if (event.classification == MotionEvent.CLASSIFICATION_AMBIGUOUS_GESTURE) {
            gesture.slop * gesture.slopMultiplier
        } else gesture.slop
        when (gesture.recognizer.move(event.rawX, event.rawY, slop)) {
            TopPullGesture.Decision.WAIT -> return false
            TopPullGesture.Decision.REJECT -> { gestures.remove(view); return false }
            TopPullGesture.Decision.SWITCH -> Unit
        }
        val windowController = view.getObj("mInteractionEventHandler").getObj("this\$0")!!
        val controller = gesture.controller
        val controlExpanded = controller.call("getControlCenterExpanded") as Boolean
        if (!allowed(windowController, controller) ||
            controlExpanded == controller.call("getNotificationExpanded") ||
            controlExpanded == gesture.recognizer.toControl) {
            gestures.remove(view)
            return false
        }

        gesture.claimed = true
        val cancel = MotionEvent.obtain(event)
        try {
            cancel.action = MotionEvent.ACTION_CANCEL
            cancelling = true
            view.dispatchTouchEvent(cancel)
        } finally {
            cancelling = false
            cancel.recycle()
        }
        // 原生负责显示/隐藏、阶梯动画、进度与完成回调；与横向循环开关独立。
        controller.call(if (gesture.recognizer.toControl) "switchToControlCenter" else "switchToNotification")
        return true
    }

    private fun allowed(windowController: Any, controller: Any): Boolean =
        !controller.getObjAs<Boolean>("switching") && !controller.getObjAs<Boolean>("dragging") &&
            !windowController.getObjAs<Boolean>("mExpandAnimationRunning") &&
            !windowController.getObjAs<Boolean>("mIsOcclusionTransitionRunning") &&
            windowController.getObj("mStatusBarStateController").call("getState") == 0 &&
            windowController.getObj("mStatusBarStateController").call("isDozing") == false &&
            windowController.getObj("mPrimaryBouncerInteractor").call("isBouncerShowing") == false &&
            windowController.getObj("mService").call("shouldIgnoreTouch") == false &&
            controller.call("isSwitchable") == true

    @SuppressLint("DiscouragedApi", "InternalInsetResource")
    private fun start(view: View, event: MotionEvent, configs: Class<*>): Gesture? {
        val windowController = view.getObj("mInteractionEventHandler").getObj("this\$0")!!
        val touchHandler = windowController.getObj("mShadeTouchHandler")!!
        val controller = touchHandler.getObj("shadeSwitchControllerImpl")!!
        val controlExpanded = controller.call("getControlCenterExpanded") as Boolean
        if (controlExpanded == controller.call("getNotificationExpanded") || !allowed(windowController, controller)) return null
        val events = touchHandler.getObj("controlCenterEventHandlerImpl")!!
        val settings = events.getObj("controlCenterSettingsController")!!
        if (settings.call("isUseControlCenter") != true || settings.call("isPanelEnabled") != true) return null
        val resources = view.resources
        val heightId = resources.getIdentifier("status_bar_height", "dimen", "com.android.systemui")
            .takeIf { it != 0 } ?: resources.getIdentifier("status_bar_height", "dimen", "android")
        val top = maxOf(if (heightId == 0) 0 else resources.getDimensionPixelSize(heightId),
            view.rootWindowInsets?.getInsetsIgnoringVisibility(WindowInsets.Type.statusBars())?.top ?: 0).toFloat()
        if (event.rawY < 0 || event.rawY >= top) return null
        // 与原生 handleExpandEvent 相同的热区入参，包含折叠小屏的刘海边距和 RTL。
        val width = resources.displayMetrics.widthPixels.toFloat()
        val container = events.getObj("container").call("get") as View
        val cutout = container.rootWindowInsets?.displayCutout
        val tinyFlip = events.getObjAs<Boolean>("isFlipDevice") &&
            configs.callS("isTinyScreen", view.context, false) == true
        val left = if (tinyFlip) (cutout?.safeInsetLeft ?: 0).toFloat() else 0f
        val right = if (tinyFlip) (cutout?.safeInsetRight ?: 0).toFloat() else 0f
        val rtl = resources.configuration.layoutDirection == View.LAYOUT_DIRECTION_RTL
        val zone = events.call("calculateCtrlHotZone", left, width - left - right, rtl)!!
        val controlStart = zone.call("getFirst") as Float
        val controlEnd = (zone.call("getSecond") as Float) +
            if (tinyFlip && !rtl && configs.callS("isCutoutRight", view.context) == true) right else 0f
        val recognizer = TopPullGesture.start(event.rawX, event.rawY, top, width,
            controlStart, controlEnd, controlExpanded) ?: return null
        val configuration = ViewConfiguration.get(view.context)
        return Gesture(controller, recognizer, event.downTime, view.width, view.height,
            configuration.scaledTouchSlop.toFloat(), configuration.scaledAmbiguousGestureMultiplier)
    }

    private fun unhookWhenIdle() {
        if (closed && gestures.values.none { it.claimed }) {
            gestures.clear()
            hook?.unhook()
            hook = null
        }
    }

    fun close(): Boolean {
        fun cleanup() {
            closed = true
            gestures.entries.removeAll { !it.value.claimed }
            // 已接管的尾流保留消费到 UP/CANCEL；此后不再调用任何原生切页方法。
            unhookWhenIdle()
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            cleanup()
            return true
        }
        val done = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { try { cleanup() } finally { done.countDown() } }
        return done.await(5, TimeUnit.SECONDS)
    }
}
