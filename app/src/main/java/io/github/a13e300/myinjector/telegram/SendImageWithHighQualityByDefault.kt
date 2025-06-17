package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAll
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
        val photoAttachPhotoCellClass = findClass("org.telegram.ui.Cells.PhotoAttachPhotoCell")
        photoAttachPhotoCellClass.hookAll(
            "setHighQuality",
            cond = ::isEnabled,
            before = { param ->
                param.thisObject.getObj("photoEntry").setObj("highQuality", false)
            },
            after = { param ->
                param.thisObject.getObj("photoEntry").setObj("highQuality", true)
            }
        )
        photoAttachPhotoCellClass.hookAll(
            "setPhotoEntry",
            cond = ::isEnabled ,
            before = { param ->
                param.thisObject.getObj("photoEntry").setObj("highQuality", false)
            },
            after = { param ->
                param.thisObject.getObj("photoEntry").setObj("highQuality", true)
            }
        )
    }
}
