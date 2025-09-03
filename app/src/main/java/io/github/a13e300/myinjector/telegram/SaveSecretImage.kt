@file:Suppress("UNCHECKED_CAST")

package io.github.a13e300.myinjector.telegram

import android.view.View
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore

class SaveSecretImage : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.saveSecretMedia

    override fun onHook() {
        val chatActivity = findClass("org.telegram.ui.ChatActivity")
        val rString = findClass("org.telegram.messenger.R\$string")
        val saveToGalleryStrId = rString.getObjSAs<Int>("SaveToGallery")
        val rDrawable = findClass("org.telegram.messenger.R\$drawable")
        val msgGalleryDrawableId = rDrawable.getObjSAs<Int>("msg_gallery")
        val localeController = findClass("org.telegram.messenger.LocaleController")
        val OPTION_SAVE_TO_GALLERY = 4 // chatActivity.getObjSAs<Int>("OPTION_SAVE_TO_GALLERY")
        val MY_OPTION_OPEN_AS_PHOTO = 8989110
        chatActivity.hookAllAfter("fillMessageMenu", cond = ::isFeatureEnabled) { param ->

            val selectedObject = param.thisObject.getObj("selectedObject")

            // val type = param.thisObject.call("getMessageType", selectedObject) as Int
            // if (type != 4) return@hookAllAfter
            if (!(selectedObject.call("needDrawBluredPreview") as Boolean)) return@hookAllAfter

            val icons = param.args[1] as ArrayList<Int> // Int
            val items = param.args[2] as ArrayList<CharSequence> // CharSequence
            val options = param.args[3] as ArrayList<Int> // Int

            items.add(localeController.callS("getString", saveToGalleryStrId) as CharSequence)
            options.add(OPTION_SAVE_TO_GALLERY)
            icons.add(msgGalleryDrawableId)

            items.add("作为正常媒体打开")
            options.add(MY_OPTION_OPEN_AS_PHOTO)
            icons.add(msgGalleryDrawableId)
        }

        val photoViewer = findClass("org.telegram.ui.PhotoViewer")
        chatActivity.hookAllBefore("processSelectedOption", cond = ::isFeatureEnabled) { param ->
            if (param.args[0] != MY_OPTION_OPEN_AS_PHOTO) return@hookAllBefore
            val instance = photoViewer.callS("getInstance")
            val ca = param.thisObject
            val selectedObject = ca.getObj("selectedObject")
            instance.call("setParentActivity", ca, ca.getObj("themeDelegate"))
            val typeIsN0 = selectedObject.getObj("type") != 0
            instance.call(
                "openPhoto",
                selectedObject,
                ca,
                if (typeIsN0) ca.getObj("dialog_id") else 0,
                if (typeIsN0) ca.getObj("mergeDialogId") else 0,
                if (typeIsN0) ca.call("getTopicId") else 0,
                ca.getObj("photoViewerProvider")
            )
        }

        // allow save
        photoViewer.hookAfter(
            "setIsAboutToSwitchToIndex",
            Integer.TYPE,
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE,
            java.lang.Boolean.TYPE, cond = ::isFeatureEnabled
        ) { param ->
            param.thisObject.getObjAs<View>("galleryButton").visibility = View.VISIBLE
            param.thisObject.getObjAs<View>("galleryGap").visibility = View.VISIBLE
        }
    }
}