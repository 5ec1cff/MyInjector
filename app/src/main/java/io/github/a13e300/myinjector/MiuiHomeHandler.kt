package io.github.a13e300.myinjector

import android.app.IActivityManager
import android.content.ComponentName
import android.os.Handler
import android.os.Looper
import android.os.ServiceManager
import android.view.View
import android.widget.TextView
import android.widget.Toast
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllAfter

class MiuiHomeHandler : IHook() {
    override fun onHook(lpparam: XC_LoadPackage.LoadPackageParam) {
        logD("handleLoadPackage: hooked")
        runCatching {
            val recentTouchHandlerClass = findClass(
                "com.miui.home.recents.views.TaskStackViewTouchHandler"
            )

            recentTouchHandlerClass.hookAllAfter("onBeginDrag") { param ->
                onBeginDrag(param.args[0] as View)
            }

            recentTouchHandlerClass.hookAllAfter("onDragEnd") { param ->
                onDragEnd(param.args[0] as View)
            }

            recentTouchHandlerClass.hookAllAfter("onChildDismissedEnd") { param ->
                onChildDismissedEnd(param.args[0] as View)
            }

            recentTouchHandlerClass.hookAllAfter("onDragCancelled") { param ->
                onDragCancelled(param.args[0] as View)
            }
        }.onFailure {
            logE("handleLoadPackage: ", it)
        }
    }

    private var handler = Handler(Looper.getMainLooper())
    private var mCurrentView: View? = null
    private var origText: CharSequence? = null
    private var setViewHeaderRunnable = Runnable { setupToBeKilled() }
    private var mToBeKilled: View? = null
    private val ams by lazy { IActivityManager.Stub.asInterface(ServiceManager.getService("activity")) }

    private fun findDismissView(): TextView =
        mCurrentView.getObj("mHeaderView").getObjAs<TextView>("mDismissView")

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
        logD("onDragCancelled: cancelled $mToBeKilled")
        mToBeKilled = null
    }

    private fun onChildDismissedEnd(v: View) {
        if (mToBeKilled != v) {
            logE("onChildDismissedEnd: not target: $v $mToBeKilled")
            return
        }
        mToBeKilled = null
        val task = v.getObj("mTask")
        val key = task.getObj("key")
        val user = key.getObjAs<Int>("userId")
        val topActivity = key.getObjAs<ComponentName>("topActivity")
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
                logE("onChildDismissedEnd: ", it)
            }
    }
}
