package io.github.a13e300.myinjector.telegram

import android.app.AndroidAppHelper
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllCAfter

// 在 emoji 和 sticker 查看页面增加更多按钮，包括查看创建者(id)和导出 emoji 信息
class EmojiStickerMenu : DynHook() {
    companion object {
        private const val MENU_DUMP = 301
        private const val MENU_GET_PROFILE = 302
    }

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.emojiStickerMenu

    override fun onHook() {
        val emojiPacksAlert = findClass("org.telegram.ui.Components.EmojiPacksAlert")
        val emojiPacksAlertEmojiPackHeader =
            findClass("org.telegram.ui.Components.EmojiPacksAlert\$EmojiPackHeader")
        val customEmojiClass =
            findClass("org.telegram.tgnet.TLRPC\$TL_documentAttributeCustomEmoji")

        emojiPacksAlertEmojiPackHeader.hookAllCAfter { param ->
            val optionsButton = param.thisObject.getObj("optionsButton") ?: return@hookAllCAfter
            optionsButton.call("addSubItem", MENU_DUMP, "Dump")
            optionsButton.call("addSubItem", MENU_GET_PROFILE, "Profile of admin")
        }

        val messagesController = findClass("org.telegram.messenger.MessagesController")

        emojiPacksAlert.hookAllBefore("onSubItemClick") { param ->
            // https://github.com/NextAlone/Nagram/blob/c189a1af80016fd3d041be121143ede94b0fdcf4/TMessagesProj/src/main/java/org/telegram/ui/Components/EmojiPacksAlert.java#L1541
            if (!isEnabled()) return@hookAllBefore
            if (param.args[0] == MENU_GET_PROFILE) {
                val stickerSet = (param.thisObject.getObj("customEmojiPacks")
                    .getObj("stickerSets") as List<*>)[0]
                val id = stickerSet.getObj("set").getObj("id") as Long
                var userId = id.shr(32)
                if (id.shr(16).and(0xff) == 0x3fL) {
                    userId = userId.or(0x80000000L)
                }
                if (id.shr(24).and(0xff) != 0L) {
                    userId += 0x100000000L
                }
                val fragment = param.thisObject.getObj("fragment")
                if (fragment != null) {
                    val user = fragment.call("getMessagesController").call("getUser", userId)
                    val currentAccount = param.thisObject.getObj("currentAccount")
                    if (user != null) {
                        messagesController.callS("getInstance", currentAccount)
                            .call("openChatOrProfileWith", user, null, fragment, 0, false)
                        return@hookAllBefore
                    }
                }
                val userLink = "tg://openmessage?user_id=$userId"
                AndroidAppHelper.currentApplication().getSystemService(
                    ClipboardManager::class.java
                ).setPrimaryClip(ClipData.newPlainText("", userLink))
                Toast.makeText(
                    AndroidAppHelper.currentApplication(), "User: $userId",
                    Toast.LENGTH_SHORT
                ).show()
            }
            if (param.args[0] != MENU_DUMP) return@hookAllBefore
            val customEmojiPacks = param.thisObject.getObj("customEmojiPacks")
            val stickerSets = customEmojiPacks.getObj("stickerSets") as List<*>
            val str = StringBuilder()
            stickerSets.firstOrNull()?.let { tlMessagesStickerSet ->
                val set = tlMessagesStickerSet.getObj("set")
                val title = set.getObj("title")
                val id = set.getObj("id")
                val shortName = set.getObj("short_name")
                // logD("dump: $title $id $shortName")
                str.append("title=")
                    .append(title)
                    .append("\nid=")
                    .append(id)
                    .append("\nshortName=")
                    .append(shortName)

                val documents = tlMessagesStickerSet.getObjAs<List<*>>(
                    "documents"
                )
                documents.forEachIndexed { i, doc ->
                    val id = doc.getObj("id")
                    val alt = doc.getObjAs<List<*>>(
                        "attributes"
                    ).firstOrNull {
                        customEmojiClass.isInstance(it)
                    }?.getObjAsN<String>("alt")
                    // logD("dump: $i id=$id alt=$alt")
                    val altUnicode = alt?.firstUnicodeChar()
                    str.append("\n$i=$id:$altUnicode")
                }

                AndroidAppHelper.currentApplication().getSystemService(
                    ClipboardManager::class.java
                ).setPrimaryClip(ClipData.newPlainText("", str.toString()))
            }
        }

        val stickersAlert = findClass("org.telegram.ui.Components.StickersAlert")

        // https://github.com/NextAlone/Nagram/blob/c189a1af80016fd3d041be121143ede94b0fdcf4/TMessagesProj/src/main/java/org/telegram/ui/Components/StickersAlert.java#L1485
        stickersAlert.hookAllAfter("init", cond = ::isEnabled) { param ->
            val optionsButton = param.thisObject.getObj("optionsButton") ?: return@hookAllAfter
            optionsButton.call("addSubItem", MENU_GET_PROFILE, "Profile of admin")
        }

        stickersAlert.hookAllBefore("onSubItemClick", cond = ::isEnabled) { param ->
            if (param.args[0] == MENU_GET_PROFILE) {
                val stickerSet = param.thisObject.getObj("stickerSet")
                val id = stickerSet.getObj("set").getObj("id") as Long
                var userId = id.shr(32)
                if (id.shr(16).and(0xff) == 0x3fL) {
                    userId = userId.or(0x80000000L)
                }
                if (id.shr(24).and(0xff) != 0L) {
                    userId += 0x100000000L
                }
                val parentFragment = param.thisObject.getObj("parentFragment")
                if (parentFragment != null) {
                    val user = parentFragment.call("getMessagesController").call("getUser", userId)
                    val currentAccount = param.thisObject.getObj("currentAccount")
                    if (user != null) {
                        messagesController.callS("getInstance", currentAccount)
                            .call("openChatOrProfileWith", user, null, parentFragment, 0, false)
                        return@hookAllBefore
                    }
                }
                val userLink = "tg://openmessage?user_id=$userId"
                AndroidAppHelper.currentApplication().getSystemService(
                    ClipboardManager::class.java
                ).setPrimaryClip(ClipData.newPlainText("", userLink))
                Toast.makeText(
                    AndroidAppHelper.currentApplication(), "User: $userId",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}
