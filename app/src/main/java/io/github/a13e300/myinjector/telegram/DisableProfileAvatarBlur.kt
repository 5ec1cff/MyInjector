package io.github.a13e300.myinjector.telegram

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
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookAllNopIf

class DisableProfileAvatarBlur : DynHook() {

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableProfileAvatarBlur

    override fun onHook() {
        // disable blur
        findClass("org.telegram.ui.Components.ProfileGalleryBlurView")
            .hookAllNopIf("draw", ::isEnabled)

        /*
        findClass("org.telegram.ui.ProfileActivity").hookAllAfter(
            "updateExtraViews",
            cond = ::isEnabled
        ) { param ->
            val pa = param.thisObject
            val overlaysView = pa.getObjAsN<View>("overlaysView") ?: return@hookAllAfter
            val actionsView = pa.getObjAsN<View>("actionsView") ?: return@hookAllAfter
            val isPulledDown = pa.getObjAs<Boolean>("isPulledDown")

            if (isPulledDown) {
                val overlaysLp = overlaysView.layoutParams
                overlaysLp.height -= actionsView.height
                overlaysView.requestLayout()
            }
        }*/

        // let avatar gallery expand to actions area
        findClass("org.telegram.ui.Components.ProfileGalleryView")
            .hookAllCAfter(cond = ::isEnabled) { param ->
                (param.thisObject as View).setPadding(0, 0, 0, 0)
            }

        // set proper shadow
        findClass("org.telegram.ui.ProfileActivity\$OverlaysView").hookAllAfter(
            "onSizeChanged",
            cond = ::isEnabled
        ) { param ->
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
            val actionsView = param.thisObject.getObjAsN<View>("actionsView")
            if (actionsView == null) {
                return@hookAllAfter
            }
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
    }
}
