package io.github.a13e300.myinjector.telegram

import android.view.View
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllNopIf

class DisableProfileAvatarBlur : DynHook() {

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.disableProfileAvatarBlur

    override fun onHook() {
        findClass("org.telegram.ui.Components.ProfileGalleryBlurView")
            .hookAllNopIf("draw", ::isEnabled)
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
        }
    }
}
