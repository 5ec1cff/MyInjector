package io.github.a13e300.myinjector.telegram

import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import de.robv.android.xposed.callbacks.XC_LoadPackage
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.R
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.logD

// 标记双向联系人（↑↓图标）
class MutualContact : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.mutualContact

    override fun onHook(param: XC_LoadPackage.LoadPackageParam) {
        val drawable = Entry.moduleRes.getDrawable(R.drawable.ic_mutual_contact)
        val tlUser = findClass("org.telegram.tgnet.TLRPC\$TL_user")
        findClass("org.telegram.ui.Cells.UserCell").hookAllAfter(
            "update",
            cond = ::isEnabled
        ) { param ->
            // logD("afterHookedMethod: update")
            val d = param.thisObject.getObjAs<Int>("currentDrawable")
            if (d != 0) {
                // logD("afterHookedMethod: currentdrawable not 0: $d")
                return@hookAllAfter
            }
            val current = param.thisObject.getObj("currentObject")
            if (!tlUser.isInstance(current)) return@hookAllAfter
            val imageView = param.thisObject.getObjAs<ImageView>("imageView")
            val mutual = current.getObjAs<Boolean>("mutual_contact")
            if (mutual) {
                imageView.setImageDrawable(drawable)
                imageView.visibility = View.VISIBLE
                (imageView.layoutParams as FrameLayout.LayoutParams).apply {
                    val resource = imageView.context.resources
                    gravity =
                        (gravity and Gravity.HORIZONTAL_GRAVITY_MASK.inv()) or Gravity.RIGHT
                    rightMargin =
                        TypedValue.applyDimension(
                            TypedValue.COMPLEX_UNIT_DIP,
                            8f,
                            resource.displayMetrics
                        ).toInt()
                    leftMargin
                }
                // logD("afterHookedMethod: set mutual contact $current")
            }
        }
        logD("hookMutualContact: done")
    }
}
