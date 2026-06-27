package io.github.a13e300.myinjector.telegram

import android.content.res.Configuration
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

    private fun isNightMode(view: View): Boolean {
        val uiMode = view.context.resources.configuration.uiMode
        return (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
    }

    private fun getActionColor(view: View): Int {
        return if (isNightMode(view)) {
            Color.WHITE
        } else {
            Color.BLACK
        }
    }

    private fun fixActionsViewAfterBlurDisabled(actionsViewObj: Any?) {
        val actionsView = actionsViewObj as? View ?: return

        val actionColor = getActionColor(actionsView)

        // 关闭 ProfileActionsView 自己的 blur 状态
        runCatching {
            actionsViewObj.call("drawingBlur", false)
        }

        // 清理可能残留的径向渐变，避免浅色主题下白底白字
        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        // 强制按日夜间模式设置四个按钮图标/文字颜色
        runCatching {
            actionsViewObj.call("setActionsColor", actionColor, false)
        }

        // 不强制改 paint alpha，避免破坏原本按钮背景观感。
        // 如果你仍然觉得按钮背景太白或太透明，可以取消下面注释微调。
        /*
        runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.alpha = if (isNightMode(actionsView)) 70 else 45
        }
        */

        actionsView.invalidate()
    }

    private fun disableExtraBlurView(viewObj: Any?) {
        runCatching {
            viewObj?.call("drawingBlur", false)
        }

        if (viewObj is View) {
            viewObj.invalidate()
        }
    }

    override fun onHook() {
        /*
         * disable blur
         *
         * 原来是直接 hookAllNopIf("draw")。
         * 但 ProfileGalleryBlurView.draw() 里除了绘制头像模糊外，
         * 还会顺带更新 actionsView 的 blur / color 状态。
         *
         * 如果直接整个 NOP，白天浅色主题 + 浅色头像时，
         * 四个按钮容易出现白底白字/图标不可见。
         *
         * 所以这里改成：
         * 1. 先手动修复 actionsView / musicView / suggestionView 的状态；
         * 2. 再阻止原始 draw 执行，从而禁用头像模糊。
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

                fixActionsViewAfterBlurDisabled(actionsView)
                disableExtraBlurView(musicView)
                disableExtraBlurView(suggestionView)

                // 阻止 ProfileGalleryBlurView.draw() 原逻辑执行
                param.result = null
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

            // 保险：每次 updateExtraViews 后也修一次按钮颜色
            fixActionsViewAfterBlurDisabled(actionsView)
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

            // 保险：动画过程中也修一次按钮颜色
            val actionsView = runCatching {
                param.thisObject.getObj("actionsView")
            }.getOrNull()
            fixActionsViewAfterBlurDisabled(actionsView)
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

                // 保险：布局过程中也修一次按钮颜色
                val actionsView = runCatching {
                    param.thisObject.getObj("actionsView")
                }.getOrNull()
                fixActionsViewAfterBlurDisabled(actionsView)
            }
        }

        // set color when pulled down
        topViewClass.hookAllAfter(
            "updateBackgroundPaint",
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) return@hookAllAfter
            val pa = param.thisObject.getObj("this\$0")
            val actionsViewObj = pa.getObj("actionsView") ?: return@hookAllAfter
            val actionsView = actionsViewObj as? View ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")

            if (isPulledDown) {
                actionsViewObj.setObj("radialGradient", null)

                // 原代码固定 Color.BLACK。
                // 这里改成日夜间动态色，避免深色主题下出现黑色图标/文字不可见。
                actionsViewObj.call("setActionsColor", getActionColor(actionsView), false)

                runCatching {
                    actionsViewObj.getObjAs<Paint>("paint").alpha = 40
                }

                actionsView.invalidate()
            }
        }
    }
}
