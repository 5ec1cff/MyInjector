package io.github.a13e300.myinjector.telegram

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.setObj
import java.util.WeakHashMap

class DisableProfileAvatarBlur : DynHook() {

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableProfileAvatarBlur

    private val extendAvatar: Boolean
        get() = TelegramHandler.settings.disableProfileAvatarBlurExtendAvatar

    /**
     * 记录 actionsView 是否处于下拉/头像展开状态。
     */
    private val actionsPulledState = WeakHashMap<View, Boolean>()

    /**
     * 缓存 Telegram 原本传给 setActionsColor(...) 的动态主题色。
     * 这个颜色会随着主题/accent 改变，不能写死。
     */
    private val normalActionsColorMap = WeakHashMap<View, Int>()

    /**
     * 防止我们自己调用 setActionsColor(...) 时被 hook 误缓存。
     */
    private var changingActionsColor = false

    private fun markActionsPulled(actionsViewObj: Any?, pulled: Boolean) {
        val actionsView = actionsViewObj as? View ?: return
        actionsPulledState[actionsView] = pulled
    }

    private fun isActionsPulled(actionsViewObj: Any?): Boolean {
        val actionsView = actionsViewObj as? View ?: return false
        return actionsPulledState[actionsView] == true
    }

    private fun cacheNormalActionsColor(actionsViewObj: Any?, color: Int) {
        val actionsView = actionsViewObj as? View ?: return

        // 只缓存普通状态颜色，避免把下拉时的白色缓存进去。
        if (isActionsPulled(actionsViewObj)) {
            return
        }

        normalActionsColorMap[actionsView] = color
    }

    /**
     * 普通未下拉状态：只清理上一轮下拉残留，不接管 Telegram 正常绘制。
     *
     * 重点：
     * - 不调用 drawingBlur(false)
     * - 不 hook onDraw
     * - 尽量不干扰头像点击/展开流程
     */
    private fun restoreActionsNormalOnce(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return

        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        // 清掉下拉状态留下的黑色半透明背景。
        runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color = Color.WHITE
            paint.alpha = 255
        }

        // 恢复 Telegram 自己的动态主题色。
        val normalColor = normalActionsColorMap[actionsView]
        if (normalColor != null) {
            runCatching {
                changingActionsColor = true
                actionsViewObj.call("setActionsColor", normalColor, false)
            }.also {
                changingActionsColor = false
            }
        }

        if (invalidate) {
            actionsView.invalidate()
        }
    }

    /**
     * 下拉/头像展开状态：强制按钮在头像图上可读。
     */
    private fun forceActionsReadableOnAvatar(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return

        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color = Color.BLACK
            paint.alpha = 88
        }

        runCatching {
            changingActionsColor = true
            actionsViewObj.call("setActionsColor", Color.WHITE, false)
        }.also {
            changingActionsColor = false
        }

        if (invalidate) {
            actionsView.invalidate()
        }
    }

    private fun applyActionsStyleByState(actionsViewObj: Any?, invalidate: Boolean = true) {
        if (isActionsPulled(actionsViewObj)) {
            forceActionsReadableOnAvatar(actionsViewObj, invalidate)
        } else {
            restoreActionsNormalOnce(actionsViewObj, invalidate)
        }
    }

    private fun disableNonActionBlurView(viewObj: Any?) {
        runCatching {
            viewObj?.call("drawingBlur", false)
        }

        if (viewObj is View) {
            viewObj.invalidate()
        }
    }

    override fun onHook() {
        /*
         * 禁用 ProfileGalleryBlurView 的头像模糊绘制。
         *
         * 注意：
         * 这里不再在普通未下拉状态频繁改 actionsView，
         * 否则容易影响头像点击/展开。
         */
        findClass("org.telegram.ui.Components.ProfileGalleryBlurView")
            .hookAllBefore("draw", cond = ::isEnabled) { param ->
                val blurView = param.thisObject

                val actionsView = runCatching {
                    blurView.getObj("actionsView")
                }.getOrNull()

                val musicView = runCatching {
                    blurView.getObj("musicView")
                }.getOrNull()

                val suggestionView = runCatching {
                    blurView.getObj("suggestionView")
                }.getOrNull()

                // 只有下拉/头像展开状态才强制黑色半透明按钮。
                if (isActionsPulled(actionsView)) {
                    forceActionsReadableOnAvatar(actionsView)
                }

                disableNonActionBlurView(musicView)
                disableNonActionBlurView(suggestionView)

                // 阻止原始模糊绘制。
                param.result = null
            }

        /*
         * 只 hook setActionsColor，用来缓存 Telegram 自己算出来的动态主题色。
         *
         * 不 hook drawingBlur。
         * 不 hook onDraw。
         * 这样可以避免重入和点击头像异常。
         */
        val profileActionsViewClass = findClass("org.telegram.ui.Components.ProfileActionsView")

        profileActionsViewClass.hookAllBefore(
            "setActionsColor",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) {
                return@hookAllBefore
            }

            val actionsViewObj = param.thisObject
            val color = param.args.getOrNull(0) as? Int ?: return@hookAllBefore

            if (!isActionsPulled(actionsViewObj)) {
                cacheNormalActionsColor(actionsViewObj, color)
            }
        }

        profileActionsViewClass.hookAllAfter(
            "setActionsColor",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) {
                return@hookAllAfter
            }

            val actionsViewObj = param.thisObject

            // 只有下拉状态才接管按钮背景和图标色。
            if (isActionsPulled(actionsViewObj)) {
                forceActionsReadableOnAvatar(actionsViewObj)
            }
        }

        /*
         * move shadow up
         */
        findClass("org.telegram.ui.ProfileActivity").hookAllAfter(
            "updateExtraViews",
            cond = ::isEnabled
        ) { param ->
            if (extendAvatar) {
                return@hookAllAfter
            }

            val pa = param.thisObject
            val overlaysView = pa.getObjAsN<View>("overlaysView") ?: return@hookAllAfter
            val actionsView = pa.getObjAsN<View>("actionsView") ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")

            markActionsPulled(actionsView, isPulledDown)

            if (isPulledDown) {
                val overlaysLp = overlaysView.layoutParams
                overlaysLp.height -= actionsView.height
                overlaysView.requestLayout()

                forceActionsReadableOnAvatar(actionsView)
            } else {
                restoreActionsNormalOnce(actionsView)
            }
        }

        /*
         * fix background turn black
         */
        val topViewClass = findClass("org.telegram.ui.ProfileActivity\$TopView")
        topViewClass.hookAllBefore("setBackgroundColor", cond = ::isEnabled) { param ->
            if (extendAvatar) {
                return@hookAllBefore
            }

            if (param.args[0] == Color.BLACK) {
                if (Throwable().stackTrace.any { it.methodName == "onAnimationEnd" }) {
                    param.result = null
                }
            }
        }

        /*
         * let avatar gallery expand to actions area
         */
        findClass("org.telegram.ui.Components.ProfileGalleryView")
            .hookAllCAfter(cond = ::isEnabled) { param ->
                if (!extendAvatar) {
                    return@hookAllCAfter
                }

                (param.thisObject as View).setPadding(0, 0, 0, 0)
            }

        /*
         * set proper shadow
         */
        findClass("org.telegram.ui.ProfileActivity\$OverlaysView").hookAllAfter(
            "onSizeChanged",
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) {
                return@hookAllAfter
            }

            val bottomOverlayGradient =
                param.thisObject.getObjAs<GradientDrawable>("bottomOverlayGradient")
            val bottomOverlayRect = param.thisObject.getObjAs<Rect>("bottomOverlayRect")
            val actionsExtraHeight =
                param.thisObject.getObj("this\$0").call("getActionsExtraHeight") as Int

            bottomOverlayRect.top -= actionsExtraHeight

            val newBounds = Rect(bottomOverlayGradient.bounds)
            newBounds.top -= actionsExtraHeight
            newBounds.bottom = bottomOverlayRect.top
            bottomOverlayGradient.bounds = newBounds
        }

        findClass("org.telegram.ui.ActionBar.ActionBar")
        val androidUtilities = findClass("org.telegram.messenger.AndroidUtilities")
        val profileActivity = findClass("org.telegram.ui.ProfileActivity")

        fun lerp(a: Float, b: Float, f: Float): Float {
            return a + f * (b - a)
        }

        /*
         * fix animation of expanding avatar
         */
        profileActivity.hookAllAfter("setAvatarExpandProgress", cond = ::isEnabled) { param ->
            if (!extendAvatar) {
                return@hookAllAfter
            }

            val pa = param.thisObject
            val avatarsViewPager = pa.getObjAs<View>("avatarsViewPager")
            val value = pa.getObjAs<Float>("currentExpandAnimatorValue")
            val avatarContainer = pa.getObjAs<View>("avatarContainer")
            val avatarScale = pa.getObjAs<Float>("avatarScale")
            val lp = avatarContainer.layoutParams as ViewGroup.MarginLayoutParams
            val realSize = avatarsViewPager.height

            val nh = lerp(
                androidUtilities.callS("dpf2", 100f) as Float,
                realSize / avatarScale,
                value
            ).toInt()

            lp.height = nh
            lp.width = nh
            lp.leftMargin = 0

            pa.call("fixAvatarImageInCenter")
            avatarContainer.requestLayout()

            val actionsView = runCatching {
                pa.getObj("actionsView")
            }.getOrNull()

            val isPulledDown = runCatching {
                pa.getObjAs<Boolean>("isPulledDown")
            }.getOrDefault(false)

            markActionsPulled(actionsView, isPulledDown)

            if (isPulledDown) {
                forceActionsReadableOnAvatar(actionsView)
            } else {
                restoreActionsNormalOnce(actionsView)
            }
        }

        profileActivity.hookAllAfter("needLayout", cond = ::isEnabled) { param ->
            if (!extendAvatar) {
                return@hookAllAfter
            }

            val pa = param.thisObject
            val openAnimationInProgress =
                pa.getObjAs<Boolean>("openAnimationInProgress")
            val playProfileAnimation = pa.getObjAs<Int>("playProfileAnimation")

            val actionsView = runCatching {
                pa.getObj("actionsView")
            }.getOrNull()

            val isPulledDown = runCatching {
                pa.getObjAs<Boolean>("isPulledDown")
            }.getOrDefault(false)

            markActionsPulled(actionsView, isPulledDown)

            if (openAnimationInProgress && playProfileAnimation == 2) {
                val avatarsViewPager = pa.getObjAs<View>("avatarsViewPager")
                val value = pa.getObjAs<Float>("currentExpandAnimatorValue")
                val avatarContainer = pa.getObjAs<View>("avatarContainer")
                val avatarScale = pa.getObjAs<Float>("avatarScale")
                val lp = avatarContainer.layoutParams as ViewGroup.MarginLayoutParams
                val realSize = avatarsViewPager.height

                val nh = lerp(
                    androidUtilities.callS("dpf2", 100f) as Float,
                    realSize / avatarScale,
                    value
                ).toInt()

                lp.height = nh
                lp.width = nh
                lp.leftMargin = 0

                pa.call("fixAvatarImageInCenter")
                avatarContainer.requestLayout()
            }

            if (isPulledDown) {
                forceActionsReadableOnAvatar(actionsView)
            } else {
                restoreActionsNormalOnce(actionsView)
            }
        }

        /*
         * updateBackgroundPaint 是 actionsView 样式经常刷新的位置。
         */
        topViewClass.hookAllAfter(
            "updateBackgroundPaint",
            cond = ::isEnabled
        ) { param ->
            val pa = param.thisObject.getObj("this\$0")
            val actionsView = pa.getObj("actionsView") ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")

            markActionsPulled(actionsView, isPulledDown)

            if (isPulledDown) {
                forceActionsReadableOnAvatar(actionsView)
            } else {
                restoreActionsNormalOnce(actionsView)
            }
        }
    }
}
