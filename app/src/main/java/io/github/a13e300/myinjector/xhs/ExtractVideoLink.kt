package io.github.a13e300.myinjector.xhs

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
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
import io.github.a13e300.myinjector.ui.InjectedDialogContext
import kotlin.math.abs
import kotlin.math.roundToInt

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
            private val positionPrefs = activity.getSharedPreferences(
                "myinjector_video_link_window", Context.MODE_PRIVATE
            )
            val lp = WindowManager.LayoutParams().apply {
                gravity = Gravity.LEFT or Gravity.TOP
                format = PixelFormat.RGBA_8888
                type = WindowManager.LayoutParams.TYPE_APPLICATION_PANEL
                alpha = 0.7f
                width = winWidth
                height = winHeight
                flags =
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            }

            init {
                restorePosition()
                val v = LayoutInflater.from(context).inflate(R.layout.floating_window, null, false)
                v.setOnClickListener {
                    runCatching {
                        val feed = locateNoteFeed(activity)
                        val video = feed.getObj("video")
                        val urlInfoList = video.getObjAs<List<*>>("urlInfoList")
                        val dialogCtx = InjectedDialogContext(context, activity)
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
            private var downX: Int = 0
            private var downY: Int = 0
            private val touchSlop = ViewConfiguration.get(activity).scaledTouchSlop
            private var dragging = false

            private fun restorePosition() {
                val metrics = activity.resources.displayMetrics
                lp.x = (metrics.widthPixels * positionPrefs.getFloat("xRatio", 1f)).roundToInt()
                    .coerceIn(0, (metrics.widthPixels - winWidth).coerceAtLeast(0))
                lp.y = (metrics.heightPixels * positionPrefs.getFloat("yRatio", 0.6f)).roundToInt()
                    .coerceIn(0, (metrics.heightPixels - winHeight).coerceAtLeast(0))
            }

            private fun savePosition() {
                val metrics = activity.resources.displayMetrics
                positionPrefs.edit()
                    .putFloat("xRatio", lp.x.toFloat() / metrics.widthPixels)
                    .putFloat("yRatio", lp.y.toFloat() / metrics.heightPixels)
                    .apply()
            }

            override fun onConfigurationChanged(newConfig: Configuration) {
                super.onConfigurationChanged(newConfig)
                restorePosition()
                if (isAttachedToWindow) wm.updateViewLayout(this, lp)
            }

            // 开始拖动后由 ViewGroup 取消子按钮的触摸，避免抬手时触发点击。
            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean =
                dragging || super.onInterceptTouchEvent(ev)

            override fun onTouchEvent(event: MotionEvent): Boolean =
                dragging || super.onTouchEvent(event)

            override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
                when (ev.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        lastX = ev.rawX.toInt()
                        lastY = ev.rawY.toInt()
                        downX = lastX
                        downY = lastY
                        dragging = false
                    }

                    MotionEvent.ACTION_MOVE, MotionEvent.ACTION_UP -> {
                        val curX = ev.rawX.toInt()
                        val curY = ev.rawY.toInt()
                        if (!dragging) {
                            dragging = abs(curX - downX) > touchSlop || abs(curY - downY) > touchSlop
                        }
                        if (!dragging) return super.dispatchTouchEvent(ev)
                        val dx = curX - lastX
                        val dy = curY - lastY
                        lastX = curX
                        lastY = curY
                        val metrics = activity.resources.displayMetrics
                        // x/y 是窗口偏移，不能累加 getLocationOnScreen 中的系统栏偏移。
                        lp.x = (lp.x + dx).coerceIn(0, (metrics.widthPixels - winWidth).coerceAtLeast(0))
                        lp.y = (lp.y + dy).coerceIn(0, (metrics.heightPixels - winHeight).coerceAtLeast(0))

                        wm.updateViewLayout(this, lp)
                    }
                }
                if (dragging &&
                    (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL)
                ) {
                    savePosition()
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
