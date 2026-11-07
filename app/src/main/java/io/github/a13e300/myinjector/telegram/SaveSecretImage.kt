@file:Suppress("UNCHECKED_CAST")

package io.github.a13e300.myinjector.telegram

import android.app.Activity
import android.view.View
import android.widget.Toast
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.newInstAs
import io.github.a13e300.myinjector.logE
import java.io.File
import java.io.FileInputStream
import java.lang.reflect.Modifier
import kotlin.concurrent.thread
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

class SaveSecretImage : MyDynHook("saveSecretMedia") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.saveSecretMedia

    override fun onHook() {
        val members = deobfSaveSecretImage(TelegramHandler.creator)
        fun method(key: String) = DexMethod(members.getValue(key).descriptor)
            .getMethodInstance(classLoader).apply { isAccessible = true }
        fun field(key: String) = DexField(members.getValue(key).descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        val selectedField = field("selected")
        val providerField = field("provider")
        val dialogField = field("dialog")
        val mergeDialogField = field("mergeDialog")
        val galleryField = field("gallery")
        val gapField = field("gap")
        val messageOwnerField = field("messageOwner")
        val messageType = method("messageType")
        val needDrawBluredPreview = telegramApiMethod(classLoader, "org.telegram.messenger.MessageObject", "needDrawBluredPreview")
        val getInstance = method("instance")
        val setParent = method("parent")
        val getTheme = method("theme")
        val getParentActivity = method("parentActivity")
        val getFileLoader = method("fileLoader")
        val getTopic = method("topic")
        val openPhoto = method("open")
        val msgGalleryDrawableId = TelegramResources.table.id("drawable", "msg_gallery")
        val MY_OPTION_OPEN_AS_PHOTO = 8989110

        val encryptedFileInputStream =
            findClass("org.telegram.messenger.secretmedia.EncryptedFileInputStream")

        val getPathToMessage = telegramApiMethod(classLoader, "org.telegram.messenger.FileLoader", "getPathToMessage", "org.telegram.tgnet.TLRPC\$Message")
        fun dumpEncryptedFile(loader: Any, obj: Any?): Boolean {
            val file = if (Modifier.isStatic(getPathToMessage.modifiers)) {
                getPathToMessage.invoke(null, obj) as File
            } else {
                getPathToMessage.invoke(loader, obj) as File
            }
            if (file.exists()) return true
            val enc = File(file.parentFile, file.name + ".enc")
            val keyPath = File(file.parentFile, enc.name + ".key")
            if (!enc.exists()) {
                logE("encrypted file not exists $obj")
                return false
            }
            if (!keyPath.exists()) {
                logE("dump encrypted failed: no key path $enc $keyPath $obj")
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
                logE("dump encrypted failed: io $file $enc $keyPath obj=$obj", t)
            }
            return false
        }

        method("fill").hookAfter(cond = ::isEnabled) { param ->
            val message = param.args[0] ?: return@hookAfter
            if (messageType.invoke(param.thisObject, message) == 2) return@hookAfter // not loaded
            if (needDrawBluredPreview.invoke(message) != true) return@hookAfter
            val icons = param.args[1] as ArrayList<Int>
            val items = param.args[2] as ArrayList<CharSequence>
            val options = param.args[3] as ArrayList<Int>
            items.add("作为正常媒体打开")
            options.add(MY_OPTION_OPEN_AS_PHOTO)
            icons.add(msgGalleryDrawableId)
        }

        method("options").hookBefore(cond = ::isEnabled) { param ->
            if (param.args[0] != MY_OPTION_OPEN_AS_PHOTO) return@hookBefore
            val ca = param.thisObject
            val message = selectedField.get(ca) ?: return@hookBefore
            val context = getParentActivity.invoke(ca) as? Activity ?: return@hookBefore
            val viewer = getInstance.invoke(null)
            // The two-argument wrapper is inlined into the surviving implementation.
            setParent.invoke(viewer, null, ca, getTheme.invoke(ca))
            val hasType = (messageType.invoke(ca, message) as Int) != 0
            val dialogId = if (hasType) dialogField.getLong(ca) else 0L
            val mergeDialogId = if (hasType) mergeDialogField.getLong(ca) else 0L
            val topicId = if (hasType) getTopic.invoke(ca) as Long else 0L
            val provider = providerField.get(ca)
            val loader = getFileLoader.invoke(ca) ?: return@hookBefore
            val owner = messageOwnerField.get(message)
            thread {
                val success = dumpEncryptedFile(loader, owner)
                context.runOnUiThread {
                    if (success) {
                        openPhoto.invoke(viewer, message, ca, dialogId, mergeDialogId, topicId, provider)
                    } else {
                        Toast.makeText(context, "failed to dump", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }

        method("switch").hookAfter(cond = ::isEnabled) { param ->
            (galleryField.get(param.thisObject) as? View)?.visibility = View.VISIBLE
            (gapField.get(param.thisObject) as? View)?.visibility = View.VISIBLE
        }
    }
}
