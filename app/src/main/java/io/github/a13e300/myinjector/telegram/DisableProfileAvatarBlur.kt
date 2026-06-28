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

class DisableProfileAvatarBlur : DynHook() {

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableProfileAvatarBlur

    private val extendAvatar: Boolean
        get() = TelegramHandler.settings.disableProfileAvatarBlurExtendAvatar

    /**
     * 修复头像区域四个按钮不可见的问题。
     *
     * 核心思路：
     * - 不再依赖 Telegram 原来的 blur 状态。
     * - 强制四个按钮使用「深色半透明背景 + 白色文字/图标」。
     * - 这个区域在头像上方，所以不按日夜间主题切换颜色。
     */
    private fun forceActionsReadable(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return

        // 清掉可能残留的径向渐变 / blur shader
        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        /*
         * 强制按钮图标/文字为白色。
         *
         * 注意：
         * 这里不要用日夜间判断。
         * 四个按钮显示在头像上方，应该始终按浮层按钮处理。
         */
        runCatching {
            actionsViewObj.call("setActionsColor", Color.WHITE, false)
        }

        /*
         * 强制按钮背景为深色半透明。
         *
         * 之前白天浅色主题下出问题，本质就是按钮背景/内容颜色都太浅。
         * 这里直接把 ProfileActionsView 的 paint 改成黑色半透明。
         */
        runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color = Color.BLACK
            paint.alpha = 88
        }

        if (invalidate) {
            actionsView.invalidate()
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
         * 但是不能像原来一样简单 NOP 掉 draw 后就不管。
         * 因为 draw 原本会影响 actionsView 的状态。
         *
         * 这里做法：
         * - draw 执行前，强制修复 actionsView 的按钮颜色和背景；
         * - musicView / suggestionView 仍然可以关闭 blur；
         * - 最后阻止原 draw 执行。
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

                forceActionsReadable(actionsView)
                disableNonActionBlurView(musicView)
                disableNonActionBlurView(suggestionView)

                // 阻止 ProfileGalleryBlurView.draw() 原逻辑执行
                param.result = null
            }

        /*
         * 关键补丁：
         *
         * 有些情况下，ProfileActionsView 自己会在 drawingBlur / onDraw / setActionsColor
         * 之后重新把颜色改回 Telegram 的浅色主题状态。
         *
         * 所以这里直接 hook ProfileActionsView 本身，
         * 每次它准备绘制或更新颜色时，都再次强制成深色半透明按钮。
         */
        val profileActionsViewClass = findClass("org.telegram.ui.Components.ProfileActionsView")

        profileActionsViewClass.hookAllAfter(
            "drawingBlur",
            cond = ::isEnabled
        ) { param ->
            forceActionsReadable(param.thisObject)
        }

        profileActionsViewClass.hookAllBefore(
            "onDraw",
            cond = ::isEnabled
        ) { param ->
            // onDraw 里不要 invalidate，避免无意义重复刷新
            forceActionsReadable(param.thisObject, invalidate = false)
        }

        profileActionsViewClass.hookAllAfter(
            "setActionsColor",
            cond = ::isEnabled
        ) { param ->
            /*
             * 防止 Telegram 后续又把按钮改成浅色主题颜色。
             * 这里不再调用 setActionsColor，避免递归。
             * 只修 paint 背景。
             */
            val actionsViewObj = param.thisObject
            val actionsView = actionsViewObj as? View ?: return@hookAllAfter

            runCatching {
                actionsViewObj.setObj("radialGradient", null)
            }

            runCatching {
                val paint = actionsViewObj.getObjAs<Paint>("paint")
                paint.color = Color.BLACK
                paint.alpha = 88
            }

            actionsView.invalidate()
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

            if (isPulledDown) {
                val overlaysLp = overlaysView.layoutParams
                overlaysLp.height -= actionsView.height
                overlaysView.requestLayout()
            }

            forceActionsReadable(actionsView)
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
            val avatarsViewPager = param.thisObject.getObjAs<View>("avatarsViewPager")
            val value = param.thisObject.getObjAs<Float>("currentExpandAnimatorValue")
            val avatarContainer = param.thisObject.getObjAs<View>("avatarContainer")
            val avatarScale = param.thisObject.getObjAs<Float>("avatarScale")
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

            param.thisObject.call("fixAvatarImageInCenter")
            avatarContainer.requestLayout()

            val actionsView = runCatching {
                param.thisObject.getObj("actionsView")
            }.getOrNull()
            forceActionsReadable(actionsView)
        }

        profileActivity.hookAllAfter("needLayout", cond = ::isEnabled) { param ->
            if (!extendAvatar) return@hookAllAfter
            val openAnimationInProgress =
                param.thisObject.getObjAs<Boolean>("openAnimationInProgress")
            val playProfileAnimation = param.thisObject.getObjAs<Int>("playProfileAnimation")

            if (openAnimationInProgress && playProfileAnimation == 2) {
                val avatarsViewPager = param.thisObject.getObjAs<View>("avatarsViewPager")
                val value = param.thisObject.getObjAs<Float>("currentExpandAnimatorValue")
                val avatarContainer = param.thisObject.getObjAs<View>("avatarContainer")
                val avatarScale = param.thisObject.getObjAs<Float>("avatarScale")
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

                param.thisObject.call("fixAvatarImageInCenter")
                avatarContainer.requestLayout()

                val actionsView = runCatching {
                    param.thisObject.getObj("actionsView")
                }.getOrNull()
                forceActionsReadable(actionsView)
            }
        }

        // set color to readable when pulled down
        topViewClass.hookAllAfter(
            "updateBackgroundPaint",
            cond = ::isEnabled
        ) { param ->
            val pa = param.thisObject.getObj("this\$0")
            val actionsView = pa.getObj("actionsView") ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")

            if (isPulledDown || extendAvatar) {
                forceActionsReadable(actionsView)
            }
        }
    }
}
