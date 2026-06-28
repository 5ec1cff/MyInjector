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
     * 记录 actionsView 是否处于下拉状态。
     *
     * true:
     *   按钮浮在头像大图上，需要深色半透明背景 + 白色图标文字。
     *
     * false:
     *   普通未下拉状态，让按钮恢复 Telegram 原本的白色背景 + 主题色图标。
     */
    private val actionsPulledState = WeakHashMap<View, Boolean>()

    /**
     * 缓存 Telegram 原本给 setActionsColor(...) 传入的动态主题色。
     * 这个颜色会随主题/accent 变化，所以不能写死。
     */
    private val normalActionsColorMap = WeakHashMap<View, Int>()

    /**
     * 防止我们自己调用 setActionsColor(...) 时被 hook 误缓存或递归处理。
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

        // 只在普通未下拉状态缓存主题色，避免把下拉状态的白色缓存进去。
        if (isActionsPulled(actionsViewObj)) {
            return
        }

        normalActionsColorMap[actionsView] = color
    }

    /**
     * 普通未下拉状态：恢复正常按钮样式。
     *
     * 注意：
     * 这里不写死蓝绿色，只恢复缓存到的 Telegram 动态主题色。
     */
    private fun restoreActionsNormal(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return

        // 普通状态不应该使用头像 blur 状态。
        runCatching {
            actionsViewObj.call("drawingBlur", false)
        }

        // 清理可能残留的渐变/blur 状态。
        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        // 清掉上一次下拉状态留下的黑色半透明背景。
        // 普通状态下按钮应是白色卡片。
        runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color = Color.WHITE
            paint.alpha = 255
        }

        // 恢复 Telegram 自己算出来的主题色。
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
     * 下拉状态：强制四个按钮在头像图上可读。
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

    private fun fixActionsByPulledState(actionsViewObj: Any?, invalidate: Boolean = true) {
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
         * 不再使用 hookAllNopIf("draw")，
         * 因为 draw 原本还会影响 actionsView 状态。
         *
         * 这里手动处理 actionsView 后，再阻止原 draw 执行。
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

                /*
                 * 关键：
                 * 普通未下拉状态恢复正常白色按钮；
                 * 下拉状态才强制黑色半透明。
                 */
                fixActionsByPulledState(actionsView)

                disableNonActionBlurView(musicView)
                disableNonActionBlurView(suggestionView)

                param.result = null
            }

        /*
         * Hook ProfileActionsView。
         *
         * 只缓存 Telegram 原本动态主题色。
         * 不 hook onDraw，避免每帧污染普通状态的按钮背景。
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

            /*
             * 只有下拉状态才强制黑色半透明。
             * 普通状态不在这里动 paint，避免又变黑。
             */
            if (isActionsPulled(actionsViewObj)) {
                forceActionsReadableOnAvatar(actionsViewObj)
            }
        }

        profileActionsViewClass.hookAllAfter(
            "drawingBlur",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) {
                return@hookAllAfter
            }

            val actionsViewObj = param.thisObject

            /*
             * drawingBlur 后也只在下拉状态接管。
             * 普通状态只清理一次残留。
             */
            if (isActionsPulled(actionsViewObj)) {
                forceActionsReadableOnAvatar(actionsViewObj)
            } else {
                restoreActionsNormal(actionsViewObj)
            }
        }

        /*
         * ProfileActivity.updateExtraViews:
         * 这里能拿到真实 isPulledDown。
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
                restoreActionsNormal(actionsView)
            }
        }

        // fix background turn black
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

        // let avatar gallery expand to actions area
        findClass("org.telegram.ui.Components.ProfileGalleryView")
            .hookAllCAfter(cond = ::isEnabled) { param ->
                if (!extendAvatar) {
                    return@hookAllCAfter
                }

                (param.thisObject as View).setPadding(0, 0, 0, 0)
            }

        // set proper shadow
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

        // fix animation of expanding avatar
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
                restoreActionsNormal(actionsView)
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
                restoreActionsNormal(actionsView)
            }
        }

        /*
         * updateBackgroundPaint 是 Telegram 经常刷新顶部背景/actions 颜色的位置。
         *
         * 这里根据真实 isPulledDown 判断：
         * - 未下拉：恢复白色按钮；
         * - 下拉：黑色半透明按钮。
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
                restoreActionsNormal(actionsView)
            }
        }
    }
}
