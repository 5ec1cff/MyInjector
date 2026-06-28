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
     * 未下拉状态下 Telegram 原本的 actions 样式。
     *
     * actionsColor：
     *   Telegram 根据当前主题 / accent color 传给 setActionsColor(...) 的动态颜色。
     *
     * paintColor / paintAlpha：
     *   actionsView 背景 Paint 原本颜色和透明度。
     */
    private data class NormalActionsStyle(
        val actionsColor: Int?,
        val paintColor: Int,
        val paintAlpha: Int
    )

    /**
     * 记录每个 actionsView 当前是否处于下拉/头像展开状态。
     *
     * true：
     *   按钮浮在头像大图上，需要深色半透明背景 + 白色图标文字。
     *
     * false：
     *   普通未下拉状态，需要恢复 Telegram 原本样式。
     */
    private val actionsPulledState = WeakHashMap<View, Boolean>()

    /**
     * 缓存每个 actionsView 未下拉状态下的原始主题样式。
     */
    private val normalActionsStyleMap = WeakHashMap<View, NormalActionsStyle>()

    /**
     * 防止我们自己调用 setActionsColor 后，被 hook 再次处理导致递归/误缓存。
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

    /**
     * 缓存 Telegram 未下拉状态下的正常样式。
     *
     * 注意：
     * - 只在未下拉状态缓存；
     * - 避免把下拉状态下的白色图标/黑色半透明背景缓存进去。
     */
    private fun cacheNormalActionsStyle(
        actionsViewObj: Any?,
        colorFromSetActionsColor: Int? = null
    ) {
        val actionsView = actionsViewObj as? View ?: return

        if (isActionsPulled(actionsViewObj)) {
            return
        }

        val paintStyle = runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color to paint.alpha
        }.getOrElse {
            Color.WHITE to 255
        }

        val oldStyle = normalActionsStyleMap[actionsView]

        normalActionsStyleMap[actionsView] = NormalActionsStyle(
            actionsColor = colorFromSetActionsColor ?: oldStyle?.actionsColor,
            paintColor = paintStyle.first,
            paintAlpha = paintStyle.second
        )
    }

    /**
     * 未下拉状态：恢复 Telegram 原本样式。
     *
     * 这里不写死蓝绿色，而是使用缓存到的 Telegram 主题色。
     */
    private fun restoreActionsNormal(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return
        val normalStyle = normalActionsStyleMap[actionsView]

        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            if (normalStyle != null) {
                paint.color = normalStyle.paintColor
                paint.alpha = normalStyle.paintAlpha
            } else {
                /**
                 * 兜底：
                 * 如果还没缓存到正常样式，至少保证未下拉按钮背景是白色。
                 */
                paint.color = Color.WHITE
                paint.alpha = 255
            }
        }

        /**
         * 恢复 Telegram 原本动态主题色。
         *
         * 如果还没缓存到 actionsColor，就不强行改图标/文字颜色，
         * 让 Telegram 自己当前状态继续生效。
         */
        val normalColor = normalStyle?.actionsColor
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
     * 下拉/头像展开状态：强制四个按钮在头像图上可读。
     *
     * 用深色半透明背景 + 白色图标文字。
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

    /**
     * 根据当前下拉状态选择按钮样式。
     */
    private fun fixActionsStyle(actionsViewObj: Any?, invalidate: Boolean = true) {
        if (isActionsPulled(actionsViewObj)) {
            forceActionsReadableOnAvatar(actionsViewObj, invalidate)
        } else {
            restoreActionsNormal(actionsViewObj, invalidate)
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
         * 禁用头像模糊绘制。
         *
         * 不能再简单 hookAllNopIf("draw")，
         * 因为 ProfileGalleryBlurView.draw() 原本会间接更新 actionsView 的状态。
         *
         * 这里在 NOP draw 前，先修正 actionsView / musicView / suggestionView 状态。
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

                fixActionsStyle(actionsView)
                disableNonActionBlurView(musicView)
                disableNonActionBlurView(suggestionView)

                // 阻止 ProfileGalleryBlurView.draw() 原始模糊逻辑执行
                param.result = null
            }

        /*
         * Hook ProfileActionsView。
         *
         * 目的：
         * - 缓存 Telegram 自己算出来的动态主题色；
         * - 防止禁用 blur 后按钮状态错乱；
         * - 下拉状态保持可读。
         */
        val profileActionsViewClass = findClass("org.telegram.ui.Components.ProfileActionsView")

        profileActionsViewClass.hookAllBefore(
            "setActionsColor",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) return@hookAllBefore

            val actionsViewObj = param.thisObject
            val color = param.args.getOrNull(0) as? Int ?: return@hookAllBefore

            /*
             * 只在未下拉状态缓存 Telegram 的动态主题色。
             */
            if (!isActionsPulled(actionsViewObj)) {
                cacheNormalActionsStyle(actionsViewObj, color)
            }
        }

        profileActionsViewClass.hookAllAfter(
            "setActionsColor",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) return@hookAllAfter

            val actionsViewObj = param.thisObject

            /*
             * setActionsColor 后，只修背景。
             *
             * 普通状态：
             *   不再写死图标/文字颜色，由 Telegram 动态主题色负责。
             *
             * 下拉状态：
             *   背景改成黑色半透明，图标文字稍后由 fixActionsStyle 改成白色。
             */
            if (isActionsPulled(actionsViewObj)) {
                runCatching {
                    actionsViewObj.setObj("radialGradient", null)
                }
                runCatching {
                    val paint = actionsViewObj.getObjAs<Paint>("paint")
                    paint.color = Color.BLACK
                    paint.alpha = 88
                }
            } else {
                val actionsView = actionsViewObj as? View
                val normalStyle = if (actionsView != null) {
                    normalActionsStyleMap[actionsView]
                } else {
                    null
                }

                runCatching {
                    actionsViewObj.setObj("radialGradient", null)
                }

                runCatching {
                    val paint = actionsViewObj.getObjAs<Paint>("paint")
                    if (normalStyle != null) {
                        paint.color = normalStyle.paintColor
                        paint.alpha = normalStyle.paintAlpha
                    } else {
                        paint.color = Color.WHITE
                        paint.alpha = 255
                    }
                }
            }

            if (actionsViewObj is View) {
                actionsViewObj.invalidate()
            }
        }

        profileActionsViewClass.hookAllAfter(
            "drawingBlur",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) return@hookAllAfter
            fixActionsStyle(param.thisObject)
        }

        profileActionsViewClass.hookAllBefore(
            "onDraw",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) return@hookAllBefore

            /*
             * 绘制前再保证一下样式正确。
             * 这里不要 invalidate，避免绘制循环。
             */
            fixActionsStyle(param.thisObject, invalidate = false)
        }

        // move shadow up
        findClass("org.telegram.ui.ProfileActivity").hookAllAfter(
            "updateExtraViews",
            cond = ::isEnabled
        ) { param ->
            if (extendAvatar) return@hookAllAfter

            val pa = param.thisObject
            val overlaysView = pa.getObjAsN<View>("overlaysView") ?: return@hookAllAfter
            val actionsView = pa.getObjAsN<View>("actionsView") ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")

            /*
             * 先标记当前状态。
             * 如果是未下拉，再缓存正常样式。
             */
            markActionsPulled(actionsView, isPulledDown)

            if (!isPulledDown) {
                cacheNormalActionsStyle(actionsView)
            }

            if (isPulledDown) {
                val overlaysLp = overlaysView.layoutParams
                overlaysLp.height -= actionsView.height
                overlaysView.requestLayout()
            }

            fixActionsStyle(actionsView)
        }

        // fix background turn black
        val topViewClass = findClass("org.telegram.ui.ProfileActivity\$TopView")
        topViewClass.hookAllBefore("setBackgroundColor", cond = ::isEnabled) { param ->
            if (extendAvatar) return@hookAllBefore
            if (param.args[0] == Color.BLACK) {
                if (Throwable().stackTrace.any { it.methodName == "onAnimationEnd" }) {
                    param.result = null
                }
            }
        }

        // let avatar gallery expand to actions area
        findClass("org.telegram.ui.Components.ProfileGalleryView")
            .hookAllCAfter(cond = ::isEnabled) { param ->
                if (!extendAvatar) return@hookAllCAfter
                (param.thisObject as View).setPadding(0, 0, 0, 0)
            }

        // set proper shadow
        findClass("org.telegram.ui.ProfileActivity\$OverlaysView").hookAllAfter(
            "onSizeChanged",
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) return@hookAllAfter
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

        fun lerp(a: Float, b: Float, f: Float) = a + f * (b - a)

        // fix animation of expanding avatar
        profileActivity.hookAllAfter("setAvatarExpandProgress", cond = ::isEnabled) { param ->
            if (!extendAvatar) return@hookAllAfter

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

            if (!isPulledDown) {
                cacheNormalActionsStyle(actionsView)
            }

            fixActionsStyle(actionsView)
        }

        profileActivity.hookAllAfter("needLayout", cond = ::isEnabled) { param ->
            if (!extendAvatar) return@hookAllAfter

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

            if (!isPulledDown) {
                cacheNormalActionsStyle(actionsView)
            }

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

            fixActionsStyle(actionsView)
        }

        /*
         * updateBackgroundPaint 是 Telegram 经常刷新顶部背景 / actionsView 颜色的地方。
         *
         * 这里根据 isPulledDown 区分：
         * - 未下拉：缓存并恢复 Telegram 原主题样式；
         * - 已下拉：深色半透明背景 + 白色文字图标。
         */
        topViewClass.hookAllAfter(
            "updateBackgroundPaint",
            cond = ::isEnabled
        ) { param ->
            val pa = param.thisObject.getObj("this\$0")
            val actionsView = pa.getObj("actionsView") ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")

            markActionsPulled(actionsView, isPulledDown)

            if (!isPulledDown) {
                cacheNormalActionsStyle(actionsView)
            }

            fixActionsStyle(actionsView)
        }
    }
}
