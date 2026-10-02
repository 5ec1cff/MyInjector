package io.github.a13e300.myinjector.telegram

import android.view.View
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.setObj
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

class SendImageWithHighQualityByDefault : MyDynHook("SendImageWithHighQualityByDefault") {
    override fun isFeatureEnabled(): Boolean =
        TelegramHandler.settings.sendImageWithHighQualityByDefault

    override fun onHook() {
        val classMediaEditState =
            findClass("org.telegram.messenger.MediaController\$MediaEditState")
        classMediaEditState.hookAllCAfter(cond = ::isEnabled) { param ->
            param.thisObject.setObj("highQuality", true)
        }
        classMediaEditState.hookAfter("reset", cond = ::isEnabled) { param ->
            param.thisObject.setObj("highQuality", true)
        }
        val creator = TelegramHandler.creator
        val setHighQuality = creator.create("PhotoAttachPhotoCellSetHighQuality") { bridge ->
            bridge.findMethod { matcher {
                paramTypes("boolean")
                returnType("void")
                addInvoke { descriptor("Lorg/telegram/messenger/MediaController\$MediaEditState;->isHighQuality()Z") }
                addInvoke { descriptor("Lorg/telegram/messenger/AndroidUtilities;->formatShortDuration(I)Ljava/lang/String;") }
            } }.single().toObfsInfo()
        }
        val photoEntryField = creator.create("PhotoAttachPhotoCellPhotoEntry") { bridge ->
            bridge.findField { matcher {
                declaredClass(setHighQuality.className)
                type("org.telegram.messenger.MediaController\$PhotoEntry")
            } }.single().toObfsInfo()
        }
        val videoInfo = creator.create("PhotoAttachPhotoCellVideoInfoContainer") { bridge ->
            // The badge is the only FrameLayout subclass used by setHighQuality.
            bridge.getMethodData(setHighQuality.descriptor)!!.usingFields.single {
                bridge.getClassData(it.field.typeName)?.superClass?.name == "android.widget.FrameLayout"
            }.field.toObfsInfo()
        }
        val photoEntry = DexField(photoEntryField.descriptor).getFieldInstance(classLoader)
            .apply { isAccessible = true }
        val videoInfoContainerField = DexField(videoInfo.descriptor).getFieldInstance(classLoader)
            .apply { isAccessible = true }
        DexMethod(setHighQuality.descriptor).getMethodInstance(classLoader)
            .hookAfter(cond = ::isEnabled) { param ->
            val entry = photoEntry.get(param.thisObject)
            val isVideo = entry?.getObj("isVideo") == true
            val highQuality = entry?.getObj("highQuality") == true
            val videoInfoContainer = videoInfoContainerField.get(param.thisObject) as? View
            if (!isVideo && highQuality) {
                videoInfoContainer?.visibility = View.INVISIBLE
            }
        }
    }
}
