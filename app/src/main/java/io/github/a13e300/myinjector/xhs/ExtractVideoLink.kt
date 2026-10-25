package io.github.a13e300.myinjector.xhs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.graphics.PixelFormat
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import io.github.a13e300.myinjector.BuildConfig
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.R
import io.github.a13e300.myinjector.arch.FindObjectConfiguration
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.dp2px
import io.github.a13e300.myinjector.arch.extraField
import io.github.a13e300.myinjector.arch.findBaseActivity
import io.github.a13e300.myinjector.arch.findBaseActivityOrNull
import io.github.a13e300.myinjector.arch.findPathToObject
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.logD
import io.github.a13e300.myinjector.logE
import io.github.a13e300.myinjector.ui.ModernInjectedDialogAction
import io.github.a13e300.myinjector.ui.ModernSettingsPalette
import io.github.a13e300.myinjector.ui.dp
import io.github.a13e300.myinjector.ui.modernInjectedButton
import io.github.a13e300.myinjector.ui.modernInjectedScrollContent
import io.github.a13e300.myinjector.ui.showModernInjectedDialog

class ExtractVideoLink : MyDynHook("extractVideoLink") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.extractVideoLink

    override fun onHook() {
        val noteFeedClass = findClass("com.xingin.entities.notedetail.NoteFeed")

        fun locateNoteFeed(context: Context): Any {
            val base = context.findBaseActivity()
            val presenter = base.getObj("linker")
                .getObj("controller")
                .getObj("presenter")!!
            val path = findPathToObject(
                presenter, FindObjectConfiguration(
                    targetClz = noteFeedClass, maxDepth = 4, maxTries = Int.MAX_VALUE, first = true
                )
            ).first()
            return path.obj
        }

        val detailFeedActivity = findClass("com.xingin.matrix.detail.activity.DetailFeedActivity")

        fun View.decorViewActivity(): Activity? {
            val view = this
            val contentView = (view.getObj("mContentRoot") as View)
            val activity = contentView.context.findBaseActivityOrNull() ?: return null
            if (activity.window !== view.getObj("mWindow")) return null
            return activity
        }

        @SuppressLint("ViewConstructor")
        class FloatingWindow(private val activity: Activity, private val context: Context) :
            FrameLayout(context) {
            val contentView: View
            val winWidth: Int = 40.dp2px(activity.resources).toInt()
            val winHeight: Int = winWidth
            val wm = activity.windowManager
            val lp = WindowManager.LayoutParams().apply {
                gravity = Gravity.LEFT or Gravity.TOP
                format = PixelFormat.RGBA_8888
                type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                x = context.resources.displayMetrics.widthPixels - winWidth
                y = context.resources.displayMetrics.heightPixels.times(0.9).toInt()
                alpha = 0.7f
                width = winWidth
                height = winHeight
                flags =
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }

            init {
                val v = LayoutInflater.from(context).inflate(R.layout.floating_window, null, false)
                v.setOnClickListener {
                    runCatching {
                        val feed = locateNoteFeed(activity)
                        val video = feed.getObj("video")
                        val urlInfoList = video.getObjAs<List<*>>("urlInfoList")
                        val dialogCtx = object : ContextThemeWrapper(context, R.style.AppTheme) {
                            override fun getSystemService(name: String): Any? {
                                if (name == WINDOW_SERVICE) {
                                    return activity.getSystemService(name)
                                }
                                return super.getSystemService(name)
                            }
                        }
                        val palette = ModernSettingsPalette.from(dialogCtx)
                        val links = LinearLayout(dialogCtx).apply {
                            orientation = LinearLayout.VERTICAL
                            urlInfoList.forEach { info ->
                                runCatching {
                                    val url = info.getObjAs<String>("url")
                                    val desc = info.getObjAs<String>("desc")
                                    val button =
                                        modernInjectedButton(dialogCtx, palette, desc).apply {
                                            setOnClickListener {
                                                dialogCtx.getSystemService(ClipboardManager::class.java)
                                                    .setPrimaryClip(ClipData.newPlainText("", url))
                                            }
                                        }
                                    addView(
                                        button,
                                        LinearLayout.LayoutParams(
                                            LayoutParams.MATCH_PARENT,
                                            dialogCtx.dp(46),
                                        ).apply {
                                            bottomMargin = dialogCtx.dp(8)
                                        },
                                    )
                                }.onFailure { t ->
                                    logE("add url btn $info", t)
                                }
                            }
                        }
                        showModernInjectedDialog(
                            dialogCtx,
                            "视频链接",
                            modernInjectedScrollContent(dialogCtx, links, 0.45f),
                            listOf(ModernInjectedDialogAction("关闭")),
                        )
                        return@runCatching
                    }.onFailure { t ->
                        logE("show url dialog", t)
                    }
                }
                addView(v)
                contentView = v
            }

            private var lastX: Int = 0
            private var lastY: Int = 0

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                when (ev.action) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = ev.rawX.toInt()
                        lastY = ev.rawY.toInt()
                    }

                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        val curX = ev.rawX.toInt()
                        val curY = ev.rawY.toInt()
                        val dx = curX - lastX
                        val dy = curY - lastY
                        lastX = curX
                        lastY = curY
                        val coords = IntArray(2)
                        getLocationOnScreen(coords)
                        lp.x = coords[0] + dx
                        lp.y = coords[1] + dy

                        wm.updateViewLayout(this, lp)
                    }
                }
                return super.dispatchTouchEvent(ev)
            }

            fun addToWindow() {
                wm.addView(this, lp)
            }

            fun removeFromWindow() {
                wm.removeViewImmediate(this)
            }
        }

        val decorViewClass = findClass("com.android.internal.policy.DecorView")

        decorViewClass.hookAllAfter("onAttachedToWindow", cond = ::isEnabled) { param ->
            val view = param.thisObject as View
            val activity = view.decorViewActivity() ?: return@hookAllAfter
            logD("attachedToWindow $activity")
            if (activity.javaClass !== detailFeedActivity) return@hookAllAfter
            var floatingWindow by extraField<FloatingWindow?>(activity, "floatingWindow", null)
            floatingWindow = FloatingWindow(
                activity, activity.call(
                    "createApplicationContext",
                    ApplicationInfo(activity.applicationInfo).apply {
                        packageName = BuildConfig.APPLICATION_ID
                        sourceDir = Entry.modulePath
                        publicSourceDir = Entry.modulePath
                        splitSourceDirs = null
                        splitPublicSourceDirs = null
                        splitNames = null
                    }, 0
                ) as Context
            ).apply { addToWindow() }
        }

        decorViewClass.hookAllAfter("onDetachedFromWindow") { param ->
            val view = param.thisObject as View
            val activity = view.decorViewActivity() ?: return@hookAllAfter
            logD("onDetachedFromWindow $activity")
            if (activity.javaClass !== detailFeedActivity) return@hookAllAfter
            var floatingWindow by extraField<FloatingWindow?>(activity, "floatingWindow", null)
            floatingWindow?.removeFromWindow()
        }
    }
}
