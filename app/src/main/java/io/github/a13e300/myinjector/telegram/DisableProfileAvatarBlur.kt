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

    private data class NormalActionsStyle(
        val actionsColor: Int?
    )

    private val actionsPulledState = WeakHashMap<View, Boolean>()
    private val normalActionsStyleMap = WeakHashMap<View, NormalActionsStyle>()

    private var changingActionsColor = false

    private fun markActionsPulled(actionsViewObj: Any?, pulled: Boolean) {
        val actionsView = actionsViewObj as? View ?: return
        actionsPulledState[actionsView] = pulled
    }

    private fun isActionsPulled(actionsViewObj: Any?): Boolean {
        val actionsView = actionsViewObj as? View ?: return false
        return actionsPulledState[actionsView] == true
    }

    private fun cacheNormalActionsColor(actionsViewObj: Any?, color: Int?) {
        val actionsView = actionsViewObj as? View ?: return

        if (isActionsPulled(actionsViewObj)) {
            return
        }

        if (color != null) {
            normalActionsStyleMap[actionsView] = NormalActionsStyle(
                actionsColor = color
            )
        }
    }

    /**
     * 普通未下拉状态。
     *
     * 注意：
     * 这个状态下不要长期接管 Telegram 的绘制。
     * 这里只做“清理残留黑色”的兜底恢复。
     */
    private fun restoreActionsNormal(actionsViewObj: Any?, invalidate: Boolean = true) {
        val actionsView = actionsViewObj as? View ?: return
        val normalStyle = normalActionsStyleMap[actionsView]

        /*
         * 让 ProfileActionsView 回到非 blur 状态。
         * 这个在未下拉时是合理的。
         */
        runCatching {
            actionsViewObj.call("drawingBlur", false)
        }

        /*
         * 清掉残留 radialGradient。
         */
        runCatching {
            actionsViewObj.setObj("radialGradient", null)
        }

        /*
         * 恢复普通按钮背景。
         *
         * 这里不要用黑色。
         * 如果 Telegram 后面自己会设置主题背景，它会覆盖这里；
         * 如果不会，这里至少保证不是黑色残留。
         */
        runCatching {
            val paint = actionsViewObj.getObjAs<Paint>("paint")
            paint.color = Color.WHITE
            paint.alpha = 255
        }

        /*
         * 恢复 Telegram 动态主题色。
         * 不写死蓝绿色。
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
     * 下拉/头像展开状态。
     *
     * 只有这个状态才强制黑色半透明背景。
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

    private fun fixActionsByState(actionsViewObj: Any?, invalidate: Boolean = true) {
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
         * 这里不要再无脑修改普通状态按钮。
         * 只有 actionsView 已标记为 pulled 时，才强制黑色半透明。
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

                if (isActionsPulled(actionsView)) {
                    forceActionsReadableOnAvatar(actionsView)
                }

                disableNonActionBlurView(musicView)
                disableNonActionBlurView(suggestionView)

                param.result = null
            }

        /*
         * ProfileActionsView hook。
         *
         * 注意：
         * 这里不再 hook onDraw。
         * onDraw 每帧改 paint 是导致普通状态变黑的主要原因。
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
             * 只缓存普通未下拉状态下 Telegram 自己算出的动态主题色。
             */
            if (!isActionsPulled(actionsViewObj)) {
                cacheNormalActionsColor(actionsViewObj, color)
            }
        }

        profileActionsViewClass.hookAllAfter(
            "setActionsColor",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) return@hookAllAfter

            val actionsViewObj = param.thisObject

            /*
             * 只有下拉状态才修成黑色半透明。
             * 普通状态不在这里动 paint，避免再次污染成黑色。
             */
            if (isActionsPulled(actionsViewObj)) {
                forceActionsReadableOnAvatar(actionsViewObj)
            }
        }

        profileActionsViewClass.hookAllAfter(
            "drawingBlur",
            cond = ::isEnabled
        ) { param ->
            if (changingActionsColor) return@hookAllAfter

            val actionsViewObj = param.thisObject

            /*
             * drawingBlur 后也只在下拉状态接管。
             * 未下拉状态让 Telegram 自己恢复。
             */
            if (isActionsPulled(actionsViewObj)) {
                forceActionsReadableOnAvatar(actionsViewObj)
            }
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

            markActionsPulled(actionsView, isPulledDown)

            if (isPulledDown) {
                val overlaysLp = overlaysView.layoutParams
                overlaysLp.height -= actionsView.height
                overlaysView.requestLayout()

                forceActionsReadableOnAvatar(actionsView)
            } else {
                /*
                 * 关键：
                 * 从下拉回到普通状态时，清掉上一版留下的黑色背景。
                 */
                restoreActionsNormal(actionsView)
            }
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

            if (isPulledDown) {
                forceActionsReadableOnAvatar(actionsView)
            } else {
                restoreActionsNormal(actionsView)
            }
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

            if (openAnimationInProgress && playProfileAnimation == 2) {
                val avatarsViewPager = pa.getObjAs<View>("avatarsViewPager")
                val value = pa.getObjAs<Float>("currentExpandAnimatorValue")
                val avatarContainer = pa.getObjAs<View>("avatarContainer")
                val avatarScale = pa.getObjAs<Float>("avatarScale")
                val lp = avatarContainer.layoutParams as ViewGroup.MarginLayoutParams
                val realSize = avatarsViewPager.height val nh = lerp( androidUtilities.callS("dpf2",100f) as Float, realSize / avatarScale, value ).toInt() lp.height = nh lp.width = nh lp.leftMargin =0 pa.call("fixAvatarImageInCenter") avatarContainer.requestLayout() } if (isPulledDown) { forceActionsReadableOnAvatar(actionsView) } else { restoreActionsNormal(actionsView) } } /* * updateBackgroundPaint 是最关键的位置。 * *这里根据真实 isPulledDown 判断： * - true ：下拉状态，黑色半透明背景 + 白色图标； * - false ：普通状态，恢复白色按钮，不再保持黑色。 */ topViewClass.hookAllAfter( "updateBackgroundPaint", cond = ::isEnabled ) { param -> val pa = param.thisObject.getObj("this\$0") val actionsView = pa.getObj("actionsView") ?: return@hookAllAfter val isPulledDown = pa.getObjAs<Boolean>("isPulledDown") markActionsPulled(actionsView, isPulledDown) if (isPulledDown) { forceActionsReadableOnAvatar(actionsView) } else { restoreActionsNormal(actionsView) } } }}```这版核心就是：```kotlin// 删除 onDraw 强制修改// 未下拉状态不再持续改 paint//只有 isPulledDown == true 时才黑色半透明```如果你替换后普通状态仍然黑，那基本可以确定还有别的地方在给 `paint.color = Color.BLACK`，但上一版里最可能的污染源就是 `ProfileActionsView.onDraw` hook，这版已经去掉了。
