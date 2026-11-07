package io.github.a13e300.myinjector.telegram

import android.view.View
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllCAfter
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

class SendImageWithHighQualityByDefault : MyDynHook("SendImageWithHighQualityByDefault") {
    override fun isFeatureEnabled(): Boolean =
        TelegramHandler.settings.sendImageWithHighQualityByDefault

    override fun onHook() {
        val classMediaEditState =
            telegramApiClass(classLoader, "org.telegram.messenger.MediaController\$MediaEditState")
        val quality = telegramApiField(classLoader, "org.telegram.messenger.MediaController\$MediaEditState", "highQuality")
        val video = telegramApiField(classLoader, "org.telegram.messenger.MediaController\$MediaEditState", "isVideo")
        classMediaEditState.hookAllCAfter(cond = ::isEnabled) { param ->
            quality.set(param.thisObject, true)
        }
        telegramApiMethod(classLoader, "org.telegram.messenger.MediaController\$MediaEditState", "reset").hookAfter(cond = ::isEnabled) { param ->
            quality.set(param.thisObject, true)
        }
        val creator = TelegramHandler.creator
        val setHighQuality = creator.create("PhotoAttachPhotoCellSetHighQuality") { bridge ->
            telegramNamedMethod(bridge, "org.telegram.ui.Cells.PhotoAttachPhotoCell", "setHighQuality", "boolean")
                ?.let { return@create it.toObfsInfo() }
            val isHighQuality = telegramApiMethod(bridge, "org.telegram.messenger.MediaController\$MediaEditState", "isHighQuality")
            bridge.findMethod { matcher {
                paramTypes("boolean")
                returnType("void")
                addInvoke { descriptor(isHighQuality.descriptor) }
                addUsingField { type(telegramApiClass(bridge, "org.telegram.messenger.MediaController\$PhotoEntry")) }
            } }.single().toObfsInfo()
        }
        val photoEntryField = creator.create("PhotoAttachPhotoCellPhotoEntry") { bridge ->
            bridge.findField { matcher {
                declaredClass(setHighQuality.className)
                type(telegramApiClass(bridge, "org.telegram.messenger.MediaController\$PhotoEntry"))
            } }.single().toObfsInfo()
        }
        val videoInfo = creator.create("PhotoAttachPhotoCellVideoInfoContainer") { bridge ->
            bridge.getClassData(setHighQuality.className)?.fields?.singleOrNull { it.name == "videoInfoContainer" }
                ?.let { return@create it.toObfsInfo() }
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
            val isVideo = entry != null && video.getBoolean(entry)
            val highQuality = entry != null && quality.get(entry) == true
            val videoInfoContainer = videoInfoContainerField.get(param.thisObject) as? View
            if (!isVideo && highQuality) {
                videoInfoContainer?.visibility = View.INVISIBLE
            }
        }
    }
}
