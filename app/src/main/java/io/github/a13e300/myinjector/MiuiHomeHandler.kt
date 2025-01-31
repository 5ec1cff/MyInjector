package io.github.a13e300.myinjector

import android.app.IActivityManager
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.os.ServiceManager
import android.util.Log
import android.view.View
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage

class MiuiHomeHandler : IXposedHookLoadPackage {
    companion object {
        private const val TAG = "MyInjector-MIUIHomeHandler"
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        Log.d(TAG, "handleLoadPackage: hooked")
        runCatching {
            val recentTouchHandlerClass = XposedHelpers.findClass(
                "com.miui.home.recents.views.TaskStackViewTouchHandler", lpparam.classLoader
            )

            XposedBridge.hookAllMethods(
                recentTouchHandlerClass,
                "onBeginDrag",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        onBeginDrag(param.args[0] as View)
                    }
                })

            XposedBridge.hookAllMethods(
                recentTouchHandlerClass,
                "onDragEnd",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        onDragEnd(param.args[0] as View)
                    }
                })

            XposedBridge.hookAllMethods(
                recentTouchHandlerClass,
                "onChildDismissedEnd",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        onChildDismissedEnd(param.args[0] as View)
                    }
                })

            XposedBridge.hookAllMethods(
                recentTouchHandlerClass,
                "onDragCancelled",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        onDragCancelled(param.args[0] as View)
                    }
                })
        }.onFailure {
            Log.e(TAG, "handleLoadPackage: ", it)
        }
    }

    private var handler = Handler(Looper.getMainLooper())
    private var mCurrentView: View? = null
    private var origText: CharSequence? = null
    private var setViewHeaderRunnable = Runnable { setupToBeKilled() }
    private var mToBeKilled: View? = null
    private val ams by lazy { IActivityManager.Stub.asInterface(ServiceManager.getService("activity")) }

    private fun findDismissView(): TextView {
        val v = XposedHelpers.getObjectField(mCurrentView, "mHeaderView")
        return XposedHelpers.getObjectField(v, "mDismissView") as TextView
    }

    private fun setupToBeKilled() {
        mCurrentView ?: return
        findDismissView().text = "停止进程"
        mToBeKilled = mCurrentView
    }

    private fun onBeginDrag(v: View) {
        mCurrentView = v
        mToBeKilled = null
        origText = findDismissView().text
        handler.postDelayed(setViewHeaderRunnable, 700)
    }

    private fun onDragEnd(v: View) {
        handler.removeCallbacks(setViewHeaderRunnable)
        if (origText != null) findDismissView().text = origText
        mCurrentView = null
        origText = null
    }

    private fun onDragCancelled(v: View) {
        Log.d(TAG, "onDragCancelled: cancelled $mToBeKilled")
        mToBeKilled = null
    }

    private fun onChildDismissedEnd(v: View) {
        if (mToBeKilled != v) {
            Log.e(TAG, "onChildDismissedEnd: not target: $v $mToBeKilled")
            return
        }
        mToBeKilled = null
        val task = XposedHelpers.getObjectField(v, "mTask")
        val key = XposedHelpers.getObjectField(task, "key")
        val user = XposedHelpers.getObjectField(key, "userId") as Int
        val topActivity = XposedHelpers.getObjectField(key, "topActivity") as ComponentName
        runCatching { ams.forceStopPackage(topActivity.packageName, user) }
            .onSuccess {
                Toast.makeText(v.context, "killed ${topActivity.packageName}", Toast.LENGTH_SHORT)
                    .show()
            }
            .onFailure {
                Toast.makeText(
                    v.context,
                    "killed ${topActivity.packageName} failed",
                    Toast.LENGTH_SHORT
                )
                    .show()
                Log.e(TAG, "onChildDismissedEnd: ", it)
            }
    }
}
