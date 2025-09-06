@file:Suppress("UNCHECKED_CAST")

package io.github.a13e300.myinjector.telegram

import android.app.Activity
import android.view.View
import android.widget.Toast
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.newInstAs
import io.github.a13e300.myinjector.logE
import java.io.File
import java.io.FileInputStream
import kotlin.concurrent.thread

class SaveSecretImage : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.saveSecretMedia

    override fun onHook() {
        val chatActivity = findClass("org.telegram.ui.ChatActivity")
        val rString = findClass("org.telegram.messenger.R\$string")
        rString.getObjSAs<Int>("SaveToGallery")
        val rDrawable = findClass("org.telegram.messenger.R\$drawable")
        val msgGalleryDrawableId = rDrawable.getObjSAs<Int>("msg_gallery")
        findClass("org.telegram.messenger.LocaleController")
        val MY_OPTION_OPEN_AS_PHOTO = 8989110

        val fileLoader = findClass("org.telegram.messenger.FileLoader")
        val encryptedFileInputStream =
            findClass("org.telegram.messenger.secretmedia.EncryptedFileInputStream")

        fun dumpEncryptedFile(account: Int, obj: Any?): Boolean {
            val loader = fileLoader.callS("getInstance", account)
            val file = loader.call("getPathToMessage", obj) as File
            if (file.exists()) return true
            val enc = File(file.parentFile, file.name + ".enc")
            val internalDir = fileLoader.callS("getInternalCacheDir") as File
            val keyPath = File(internalDir, enc.name + ".key")
            if (!enc.exists()) {
                logE("encrypted file not exists $obj")
                return false
            }
            if (!keyPath.exists()) {
                logE("dump encrypted failed: no key path $enc $keyPath $account $obj")
                return false
            }

            try {
                encryptedFileInputStream.newInstAs<FileInputStream>(enc, keyPath).use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                return true
            } catch (t: Throwable) {
                logE("dump encrypted failed: io $file $enc $keyPath account=$account obj=$obj", t)
            }
            return false
        }

        chatActivity.hookAllAfter("fillMessageMenu", cond = ::isFeatureEnabled) { param ->

            val selectedObject = param.thisObject.getObj("selectedObject")

            val type = param.thisObject.call("getMessageType", selectedObject) as Int
            if (type == 2) return@hookAllAfter // not loaded
            if (!(selectedObject.call("needDrawBluredPreview") as Boolean)) return@hookAllAfter

            val icons = param.args[1] as ArrayList<Int> // Int
            val items = param.args[2] as ArrayList<CharSequence> // CharSequence
            val options = param.args[3] as ArrayList<Int> // Int

            // TODO: directly save menu item
            /*
            items.add(localeController.callS("getString", saveToGalleryStrId) as CharSequence)
            options.add(OPTION_SAVE_TO_GALLERY)
            icons.add(msgGalleryDrawableId)*/

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
            val ctx = ca.call("getContext") as Activity
            thread {
                val success = dumpEncryptedFile(
                    ca.getObjAs<Int>("currentAccount"),
                    selectedObject.getObj("messageOwner")
                )
                ctx.runOnUiThread {
                    if (success) {
                        instance.call(
                            "openPhoto",
                            selectedObject,
                            ca,
                            if (typeIsN0) ca.getObj("dialog_id") else 0,
                            if (typeIsN0) ca.getObj("mergeDialogId") else 0,
                            if (typeIsN0) ca.call("getTopicId") else 0,
                            ca.getObj("photoViewerProvider")
                        )
                    } else {
                        Toast.makeText(ctx, "failed to dump", Toast.LENGTH_SHORT).show()
                    }
                }
            }
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