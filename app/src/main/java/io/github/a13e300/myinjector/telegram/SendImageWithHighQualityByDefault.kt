package io.github.a13e300.myinjector.telegram

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.setObj

class SendImageWithHighQualityByDefault : DynHook() {
    override fun isFeatureEnabled(): Boolean =
        TelegramHandler.settings.sendImageWithHighQualityByDefault

    override fun onHook() {
        val classMediaEditState =
            findClass("org.telegram.messenger.MediaController\$MediaEditState")
        classMediaEditState.hookAllCAfter(cond = ::isEnabled) { param ->
            param.thisObject.setObj("highQuality", true)
        }
        classMediaEditState.hookAllAfter("reset", cond = ::isEnabled) { param ->
            param.thisObject.setObj("highQuality", true)
        }
        val cellClass = findClass("org.telegram.ui.Cells.PhotoAttachPhotoCell")
        cellClass.hookAllAfter("setHighQuality", cond = ::isEnabled) { param ->
            val photoEntry = param.thisObject.getObj("photoEntry")
            val isVideo = photoEntry?.getObj("isVideo") == true
            val highQuality = photoEntry?.getObj("highQuality") == true
            val videoTextView = param.thisObject.getObj("videoTextView") as? TextView
            val videoInfoContainer = param.thisObject.getObj("videoInfoContainer") as? View
            val videoPlayImageView = param.thisObject.getObj("videoPlayImageView") as? View
            val layoutParams = videoTextView?.layoutParams as? ViewGroup.MarginLayoutParams
            if (!isVideo && highQuality) {
                videoInfoContainer?.visibility = View.INVISIBLE
                videoPlayImageView?.visibility = View.GONE
                layoutParams?.leftMargin = 0
                videoTextView?.visibility = View.GONE
            }
        }
    }
}
