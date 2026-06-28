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
     * Telegram 原本的 actionsView 样式。
     *
     * 注意：
     * 不同主题、不同 accent、大会员背景、个人主页背景下，
     * actionsView 的背景和图标颜色都可能不同。
     *
     * 所以这里必须缓存原样，不能写死白色背景或蓝绿色图标。
     */
    private data class ActionsOriginalStyle(
        val actionsColor: Int?,
        val paintColor: Int,
        val paintAlpha: Int
    )

    /**
     * 记录 actionsView 是否处于下拉/头像展开状态。
     */
    private val actionsPulledState = WeakHashMap<View, Boolean>()

    /**
     * 缓存 Telegram 原本的 actionsView 样式。
     */
    private val originalActionsStyleMap = WeakHashMap<View, ActionsOriginalStyle>()

    /**
     * 记录某个 actionsView 当前是否被我们强制改成了头像浮层样式。
     */
    private val forcedActionsStyleMap = WeakHashMap<View, Boolean>()

    /**
     * 防止我们自己调用 setActionsColor(...) 时，被 hook 误缓存。
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

    private fun isForcedByUs(actionsViewObj: Any?): Boolean {
        val actionsView = actionsViewObj as? View ?: return false
        return forcedActionsStyleMap[actionsView] == true
    }

    private fun setForcedByUs(actionsViewObj: Any?, forced: Boolean) {
        val actionsView = actionsViewObj as? View ?: return
        forcedActionsStyleMap[actionsView] = forced
    }

    /**
     * 缓存当前 Telegram 原本样式。
     *
     * 关键：
     * - 如果当前样式已经是我们强制设置的黑色半透明，就不能缓存；
     * - 否则会把错误状态缓存进去，恢复时还是错的。
     */
    private fun cacheOriginalActionsStyle(
        actionsViewObj: Any?,
        colorFromSetActionsColor: Int? = null
    ) {
        val actionsView = actionsViewObj as? View ?: return

        if (isForcedByUs(actionsViewObj)) {
            return
        }

        val paintInfo = runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color to paint.alpha
        }.getOrNull() ?: return

        val oldStyle = originalActionsStyleMap[actionsView]

        originalActionsStyleMap[actionsView] = ActionsOriginalStyle(
            actionsColor = colorFromSetActionsColor ?: oldStyle?.actionsColor,
            paintColor = paintInfo.first,
            paintAlpha = paintInfo.second
        )
    }

    /**
     * 恢复 Telegram 原本样式。
     *
     * 这里不再写死白色背景。
     *
     * 对普通用户：
     *   如果原本是白色按钮，就恢复白色按钮。
     *
     * 对大会员/顶部背景用户：
     *   如果原本是深色半透明按钮，就恢复深色半透明按钮。
     */
    private fun restoreActionsOriginalStyle(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return
        val originalStyle = originalActionsStyleMap[actionsView]

        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        if (originalStyle != null) {
            runCatching {
                val paint = actionsViewObj.getObjAs<Paint>("paint")
                paint.color = originalStyle.paintColor
                paint.alpha = originalStyle.paintAlpha
            }

            val originalColor = originalStyle.actionsColor
            if (originalColor != null) {
                runCatching {
                    changingActionsColor = true
                    actionsViewObj.call("setActionsColor", originalColor, false)
                }.also {
                    changingActionsColor = false
                }
            }
        }

        setForcedByUs(actionsViewObj, false)

        if (invalidate) {
            actionsView.invalidate()
        }
    }

    /**
     * 下拉/头像展开状态：强制按钮在头像图上可读。
     *
     * 在强制修改之前，会先缓存 Telegram 原本样式。
     */
    private fun forceActionsReadableOnAvatar(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return

        if (!isForcedByUs(actionsViewObj)) {
            cacheOriginalActionsStyle(actionsViewObj)
        }

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

        setForcedByUs(actionsViewObj, true)

        if (invalidate) {
            actionsView.invalidate()
        }
    }

    /**
     * 根据状态应用样式。
     *
     * pulled:
     *   强制黑色半透明 + 白色图标。
     *
     * not pulled:
     *   如果之前被我们强制过，则恢复 Telegram 原样；
     *   如果没被我们改过，则不乱动，避免破坏大会员背景样式。
     */
    private fun applyActionsStyleByState(actionsViewObj: Any?, invalidate: Boolean = true) {
        if (isActionsPulled(actionsViewObj)) {
            forceActionsReadableOnAvatar(actionsViewObj, invalidate)
        } else {
            if (isForcedByUs(actionsViewObj)) {
                restoreActionsOriginalStyle(actionsViewObj, invalidate)
            }
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
         * 普通未下拉状态不主动改 actionsView，
         * 避免把大会员/背景主页的按钮改成白盒子。
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

                applyActionsStyleByState(actionsView)

                disableNonActionBlurView(musicView)
                disableNonActionBlurView(suggestionView)

                param.result = null
            }

        /*
         * 只 hook setActionsColor：
         * - 缓存 Telegram 动态主题色；
         * - 下拉状态时保持可读。
         *
         * 不 hook onDraw。
         * 不 hook drawingBlur。
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

            /*
             * 只有在不是我们强制状态时，才缓存 Telegram 原本颜色。
             */
            if (!isForcedByUs(actionsViewObj)) {
                cacheOriginalActionsStyle(actionsViewObj, color)
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

            /*
             * 只有下拉状态才强制浮层样式。
             */
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

            if (!isForcedByUs(actionsView)) {
                cacheOriginalActionsStyle(actionsView)
            }

            markActionsPulled(actionsView, isPulledDown)

            if (isPulledDown) {
                val overlaysLp = overlaysView.layoutParams
                overlaysLp.height -= actionsView.height
                overlaysView.requestLayout()
            }

            applyActionsStyleByState(actionsView)
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

            if (!isForcedByUs(actionsView)) {
                cacheOriginalActionsStyle(actionsView)
            }

            markActionsPulled(actionsView, isPulledDown)
            applyActionsStyleByState(actionsView)
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

            if (!isForcedByUs(actionsView)) {
                cacheOriginalActionsStyle(actionsView)
            }

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

            applyActionsStyleByState(actionsView)
        }

        /*
         * updateBackgroundPaint 是 actionsView 样式经常刷新的位置。
         *
         * 这里不能在未下拉时强制白色。
         * 只能：
         * - 缓存 Telegram 原样；
         * - 下拉时强制可读；
         * - 从强制状态回来时恢复原样。
         */
        topViewClass.hookAllAfter(
            "updateBackgroundPaint",
            cond = ::isEnabled
        ) { param ->
            val pa = param.thisObject.getObj("this\$0")
            val actionsView = pa.getObj("actionsView") ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")

            if (!isForcedByUs(actionsView)) {
                cacheOriginalActionsStyle(actionsView)
            }

            markActionsPulled(actionsView, isPulledDown)
            applyActionsStyleByState(actionsView)
        }
    }
}
