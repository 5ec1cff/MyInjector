package io.github.a13e300.myinjector.telegram

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.currentApplication
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjAs
import io.github.a13e300.myinjector.arch.getObjAsN
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookAllCAfter
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

// 在 emoji 和 sticker 查看页面增加更多按钮，包括查看创建者(id)和导出 emoji 信息
class EmojiStickerMenu : MyDynHook("emojiStickerMenu") {
    companion object {
        private const val MENU_DUMP = 301
        private const val MENU_GET_PROFILE = 302
    }

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.emojiStickerMenu

    override fun onHook() {
        val creator = TelegramHandler.creator
        val found by lazy { findEmojiStickerMenu(creator.bridge, creator.obfsTable.getValue("BaseFragment").className) }
        val keys = listOf("emojiClick", "emojiOwner", "header", "emojiOptions", "loader", "sets", "emojiFragment",
            "stickerClick", "stickerOwner", "stickerInit", "stickerOptions", "stickerSet", "stickerFragment", "addText")
        val members = keys.associateWith { key -> creator.create("EmojiStickerMenu.$key") {
            val descriptor = found.getValue(key)
            when {
                descriptor.isEmpty() -> ObfsInfo("", "")
                key == "header" -> ObfsInfo(descriptor, "")
                key in listOf("emojiClick", "stickerClick", "stickerInit", "addText") -> {
                    val m = DexMethod(descriptor); ObfsInfo(m.className, m.name, descriptor)
                }
                else -> { val f = DexField(descriptor); ObfsInfo(f.className, f.name, descriptor) }
            }
        } }
        fun field(key: String) = DexField(members.getValue(key).descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        fun method(key: String) = DexMethod(members.getValue(key).descriptor)
            .getMethodInstance(classLoader).apply { isAccessible = true }
        val emojiOptions = field("emojiOptions")
        val stickerOptions = field("stickerOptions")
        val loaderField = field("loader")
        val setsField = field("sets")
        val emojiFragment = field("emojiFragment")
        val stickerFragment = field("stickerFragment")
        val stickerSetField = field("stickerSet")
        val emojiOwner = members.getValue("emojiOwner").descriptor.takeIf { it.isNotEmpty() }?.let { field("emojiOwner") }
        val stickerOwner = members.getValue("stickerOwner").descriptor.takeIf { it.isNotEmpty() }?.let { field("stickerOwner") }
        val addText = method("addText")
        val customEmojiClass = findClass("org.telegram.tgnet.TLRPC\$TL_documentAttributeCustomEmoji")
        val messagesController = findClass("org.telegram.messenger.MessagesController")

        fun showAdmin(alert: Any, stickerSet: Any?, fragment: Any?) {
            val id = stickerSet.getObj("set").getObj("id") as Long
            var userId = id.shr(32)
            if (id.shr(16).and(0xff) == 0x3fL) userId = userId.or(0x80000000L)
            if (id.shr(24).and(0xff) != 0L) userId += 0x100000000L
            if (fragment != null) {
                // Messenger members and BottomSheet.currentAccount retain their names.
                val controller = messagesController.callS("getInstance", alert.getObj("currentAccount"))
                val user = controller.call("getUser", userId)
                if (user != null) {
                    controller.call("openChatOrProfileWith", user, null, fragment, 0, false)
                    return
                }
            }
            currentApplication().getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("", "tg://openmessage?user_id=$userId"))
            Toast.makeText(currentApplication(), "User: $userId", Toast.LENGTH_SHORT).show()
        }

        findClass(members.getValue("header").className).hookAllCAfter(cond = ::isEnabled) { param ->
            val button = emojiOptions.get(param.thisObject) ?: return@hookAllCAfter
            addText.invoke(button, MENU_DUMP, "Dump")
            addText.invoke(button, MENU_GET_PROFILE, "Profile of admin")
        }
        val emojiClick = method("emojiClick")
        emojiClick.hookBefore(cond = ::isEnabled) { param ->
            val id = param.args.last() as Int
            if (id != MENU_DUMP && id != MENU_GET_PROFILE) return@hookBefore
            val receiver = if (Modifier.isStatic(emojiClick.modifiers)) param.args[0]!! else param.thisObject
            val alert = emojiOwner?.get(receiver) ?: receiver
            if (!loaderField.declaringClass.isInstance(alert)) return@hookBefore
            val loader = loaderField.get(alert) ?: return@hookBefore
            val stickerSets = setsField.get(loader) as? List<*> ?: return@hookBefore
            param.result = null
            if (id == MENU_GET_PROFILE) {
                stickerSets.firstOrNull()?.let { showAdmin(alert, it, emojiFragment.get(alert)) }
                return@hookBefore
            }
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

                currentApplication().getSystemService(
                    ClipboardManager::class.java
                ).setPrimaryClip(ClipData.newPlainText("", str.toString()))
            }
        }
        method("stickerInit").hookAfter(cond = ::isEnabled) { param ->
            val button = stickerOptions.get(param.thisObject) ?: return@hookAfter
            addText.invoke(button, MENU_GET_PROFILE, "Profile of admin")
        }
        val stickerClick = method("stickerClick")
        stickerClick.hookBefore(cond = ::isEnabled) { param ->
            if (param.args.last() != MENU_GET_PROFILE) return@hookBefore
            val receiver = if (Modifier.isStatic(stickerClick.modifiers)) param.args[0]!! else param.thisObject
            val alert = stickerOwner?.get(receiver) ?: receiver
            if (!stickerSetField.declaringClass.isInstance(alert)) return@hookBefore
            val set = stickerSetField.get(alert) ?: return@hookBefore
            param.result = null
            showAdmin(alert, set, stickerFragment.get(alert))
        }
    }
}

private fun findEmojiStickerMenu(bridge: DexKitBridge, baseFragment: String): Map<String, String> {
    val handlers = bridge.findMethod { matcher {
        returnType("void"); usingStrings("/addemoji/", "/addstickers/")
    } }.filter { it.paramTypeNames == listOf("int") || (Modifier.isStatic(it.modifiers) && it.paramTypeNames == listOf(it.className, "int")) }
    check(handlers.size == 2)
    val sticker = handlers.single { m -> m.usingFields.any { it.field.typeName == "org.telegram.tgnet.TLRPC\$TL_messages_stickerSet" } }
    val emoji = handlers.single { it != sticker }
    val stickerSet = sticker.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
        it.typeName == "org.telegram.tgnet.TLRPC\$TL_messages_stickerSet"
    }
    val stickerClass = stickerSet.className
    val sets = emoji.usingFields.map { it.field }.distinctBy { it.descriptor }.single { it.typeName == "java.util.ArrayList" }
    val loader = emoji.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
        it.typeName == sets.className || it.type.superClass?.name == sets.className
    }
    val emojiClass = loader.className
    fun capturedOwner(handler: MethodData, owner: String): String {
        if (handler.className == owner) return ""
        // R8 may keep the callback in a listener and merge its captured
        // receiver into an Object field instead of inlining it into the alert.
        return bridge.getClassData(handler.className)!!.fields.single {
            it.typeName == owner || it.typeName == "java.lang.Object"
        }.descriptor
    }
    val initializers = bridge.findMethod { matcher {
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("StickersShare") }
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("CopyLink") }
    } }
    val header = initializers.single { it.isConstructor && it.declaredClass!!.fields.any { f -> f.typeName == emojiClass } }
    val init = initializers.single { it.className == stickerClass }
    val addMenu = header.invokes.distinctBy { it.descriptor }.single { it.paramTypeNames.take(2) == listOf("int", "int") && it.paramTypeNames.lastOrNull() in listOf("java.lang.String", "java.lang.CharSequence") && it.paramCount == 3 }
    val addText = bridge.findMethod { matcher { declaredClass(addMenu.className); paramTypes("int", "java.lang.CharSequence") } }.single()
    fun field(c: String, type: String) = bridge.findField { matcher { declaredClass(c); type(type) } }.single()
    return mapOf(
        "emojiClick" to emoji.descriptor, "emojiOwner" to capturedOwner(emoji, emojiClass), "header" to header.className,
        "emojiOptions" to field(header.className, addMenu.className).descriptor,
        "loader" to loader.descriptor, "sets" to sets.descriptor,
        "emojiFragment" to field(emojiClass, baseFragment).descriptor,
        "stickerClick" to sticker.descriptor, "stickerOwner" to capturedOwner(sticker, stickerClass), "stickerInit" to init.descriptor,
        "stickerOptions" to field(stickerClass, addMenu.className).descriptor,
        "stickerSet" to stickerSet.descriptor,
        "stickerFragment" to field(stickerClass, baseFragment).descriptor,
        "addText" to addText.descriptor
    )
}
