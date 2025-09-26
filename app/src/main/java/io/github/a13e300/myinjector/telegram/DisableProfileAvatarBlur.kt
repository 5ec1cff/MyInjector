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
import io.github.a13e300.myinjector.arch.hookAllNopIf
import io.github.a13e300.myinjector.arch.setObj

class DisableProfileAvatarBlur : DynHook() {

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableProfileAvatarBlur

    private val extendAvatar: Boolean
        get() = TelegramHandler.settings.disableProfileAvatarBlurExtendAvatar

    override fun onHook() {
        // disable blur
        findClass("org.telegram.ui.Components.ProfileGalleryBlurView")
            .hookAllNopIf("draw", ::isEnabled)

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
        }

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
            val realSize = avatarsViewPager.height // extraHeight + newTop // + actionsView.height
            val nh = lerp(
                androidUtilities.callS("dpf2", 100f) as Float,
                realSize / avatarScale,
                value
            ).toInt()
            lp.height = nh
            lp.width = nh
            // make margin fixed, call fixAvatarImageInCenter to align it
            lp.leftMargin = 0

            param.thisObject.call("fixAvatarImageInCenter")
            avatarContainer.requestLayout()
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
                val realSize =
                    avatarsViewPager.height // extraHeight + newTop // + actionsView.height
                val nh = lerp(
                    androidUtilities.callS("dpf2", 100f) as Float,
                    realSize / avatarScale,
                    value
                ).toInt()
                lp.height = nh
                lp.width = nh
                // make margin fixed, call fixAvatarImageInCenter to align it
                lp.leftMargin = 0

                param.thisObject.call("fixAvatarImageInCenter")
                avatarContainer.requestLayout()
            }
        }

        // set color to black when pulled down
        topViewClass.hookAllAfter(
            "updateBackgroundPaint",
            cond = ::isEnabled
        ) { param ->
            if (!extendAvatar) return@hookAllAfter
            val pa = param.thisObject.getObj("this\$0")
            val actionsView = pa.getObj("actionsView") ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")
            if (isPulledDown) {
                actionsView.setObj("radialGradient", null)
                actionsView.call("setActionsColor", Color.BLACK, false)
                actionsView.getObjAs<Paint>("paint").alpha = 40
            }
        }
    }
}
