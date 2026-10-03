package io.github.a13e300.myinjector.telegram

import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.callOrig
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.setObj
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

// Ported from TMoe's HistoricalNewsOption, with an independent switch.
// https://github.com/cinit/TMoe
class RepeatMessage : MyDynHook("repeatMessage") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.repeatMessage

    override fun onHook() {
        val chatClass = MessageMenuHook.initialize(classLoader)
        val menu = messageMenuMembers(TelegramHandler.creator)
        val members = repeatMembers(TelegramHandler.creator, chatClass.name,
            menu.getValue("createMenu").descriptor, menu.getValue("options").descriptor)
        val sender = MessageRepeater(classLoader, members)
        MessageMenuHook.register("repeatMessage", "复读", "msg_retry", ::isEnabled,
            { onHookError(it) }, sender::action)
    }
}

private class MessageRepeater(loader: ClassLoader, members: Map<String, ObfsInfo>) {
    private val fields = repeatFields.associateWith {
        DexField(members.getValue(it).descriptor).getFieldInstance(loader).apply { isAccessible = true }
    }
    private val methods = (repeatKeys - repeatFields.toSet()).associateWith {
        DexMethod(members.getValue(it).descriptor).getMethodInstance(loader).apply { isAccessible = true }
    }
    private val mentionName = loader.loadClass("org.telegram.tgnet.TLRPC\$TL_messageEntityMentionName")
    private val inputMention = loader.loadClass("org.telegram.tgnet.TLRPC\$TL_inputMessageEntityMentionName")
        .getDeclaredConstructor()
    private val inputUser = loader.loadClass("org.telegram.messenger.MessagesController")
        .getDeclaredMethod("getInputUser", java.lang.Long.TYPE)

    fun action(chat: Any, message: Any): MessageMenuAction {
        // Snapshot the album before dismissing the menu clears selectedObjectGroup.
        val group = fields.getValue("group").get(chat)
        val messages = ArrayList<Any>()
        if (group == null) messages.add(message)
        else {
            @Suppress("UNCHECKED_CAST")
            messages.addAll(group.getObj("messages") as Collection<Any>)
            messages.sortBy { it.call("getId") as Int }
        }
        return MessageMenuAction(click = click@ { view ->
            val controller = chat.call("getMessagesController")!!
            val dialogId = fields.getValue("dialog").getLong(chat)
            val thread = fields.getValue("thread").get(chat)
            val currentChat = fields.getValue("currentChat").get(chat)
            // AntiAntiCopy hooks this API. Check its original result for this
            // destination rather than relying on its global, possibly stale flag.
            val restricted = methods.getValue("chatNoForwards").callOrig(controller, currentChat) as Boolean ||
                (dialogId > 0 && methods.getValue("userNoForwards").callOrig(controller, dialogId) as Boolean) ||
                messages.any { it.getObj("messageOwner").getObj("noforwards") == true }
            if (thread == null && !restricted) {
                try {
                    // Native forwarding handles slow mode, media restrictions
                    // and paid-message confirmation; retain TMoe's attribution.
                    methods.getValue("forward").invoke(chat, ArrayList(messages), false, false, true, 0, 0L)
                } finally {
                    MessageMenuHook.dismiss(chat)
                }
                return@click
            }

            // In threads and protected chats, TMoe repeats only the selected
            // sticker or text (including media captions), not the whole album.
            val sticker = message.call("isAnyKindOfSticker") == true &&
                message.call("isAnimatedEmoji") != true && message.call("isDice") != true
            val document = if (sticker) message.call("getDocument") else null
            val owner = message.getObj("messageOwner")
            val text = owner.getObj("message") as String?
            val sendArgs = methods.getValue("chatArgs").invoke(chat)
            val monoPeer = methods.getValue("monoPeer").invoke(chat) as Long
            val suggestion = fields.getValue("suggestion").get(chat)
            MessageMenuHook.dismiss(chat)
            if (document == null && text.isNullOrEmpty()) {
                Toast.makeText(view.context, "当前聊天仅支持复读文字或贴纸", Toast.LENGTH_SHORT).show()
                return@click
            }
            if (methods.getValue("slowMode").invoke(chat) != true) return@click
            val helper = chat.call("getSendMessagesHelper")!!
            if (document != null) {
                // Resolve ReplyQuote by the signature; its class is obfuscated.
                methods.getValue("sendSticker").invoke(helper, document, null, dialogId, thread, thread,
                    null, null, null, true, 0, 0, false, null, sendArgs, 0L, monoPeer, suggestion)
            } else {
                val params = methods.getValue("textParams").invoke(null, text, dialogId)
                params.setObj("notify", true)
                params.setObj("searchLinks", false)
                params.setObj("replyToMsg", thread)
                params.setObj("replyToTopMsg", thread)
                params.setObj("entities", copyEntities(owner.getObj("entities") as Collection<*>?, controller))
                params.setObj("sendMessageChatArguments", sendArgs)
                params.setObj("monoForumPeer", monoPeer)
                params.setObj("suggestionParams", suggestion)
                methods.getValue("sendText").invoke(helper, params)
            }
        })
    }

    private fun copyEntities(entities: Collection<*>?, controller: Any): ArrayList<Any>? {
        if (entities.isNullOrEmpty()) return null
        return entities.mapTo(ArrayList()) { entity ->
            requireNotNull(entity)
            if (mentionName.isInstance(entity)) {
                inputMention.newInstance().apply {
                    setObj("offset", entity.getObj("offset"))
                    setObj("length", entity.getObj("length"))
                    setObj("user_id", inputUser.invoke(controller, entity.getObj("user_id")))
                }
            } else entity
        }
    }
}

private val repeatFields = listOf("dialog", "thread", "suggestion", "group", "currentChat")
private val repeatKeys = repeatFields + listOf("forward", "slowMode", "monoPeer", "chatArgs",
    "sendSticker", "sendText", "textParams", "chatNoForwards", "userNoForwards")

private fun repeatMembers(creator: ObfsTableCreator, chat: String, menu: String, options: String): Map<String, ObfsInfo> {
    val found by lazy { findRepeatMessage(creator.bridge, chat, menu, options) }
    return repeatKeys.associateWith { key -> creator.create("RepeatMessage.$key") {
        val descriptor = found.getValue(key)
        if (key in repeatFields) {
            val field = DexField(descriptor)
            ObfsInfo(field.className, field.name, descriptor)
        } else {
            val method = DexMethod(descriptor)
            ObfsInfo(method.className, method.name, descriptor)
        }
    } }
}

internal fun findRepeatMessage(bridge: DexKitBridge, chat: String, createMenu: String, options: String): Map<String, String> {
    val message = "org.telegram.messenger.MessageObject"
    val suggestion = "org.telegram.messenger.MessageSuggestionParams"
    val chatArgs = "org.telegram.messenger.SendMessageChatArguments"
    val helper = "org.telegram.messenger.SendMessagesHelper"
    val params = "$helper\$SendMessageParams"
    val forward = bridge.findMethod { matcher {
        declaredClass(chat); returnType("void")
        paramTypes("java.util.ArrayList", "boolean", "boolean", "boolean", "int", "long")
        addInvoke { declaredClass(helper); name("sendMessage") }
    } }.single()
    val forwardFields = forward.usingFields.map { it.field }.distinctBy { it.descriptor }
    fun forwardField(type: String) = forwardFields.single { it.className == chat && it.typeName == type }.descriptor
    // processSelectedOption first reads the selected album in its Retry branch;
    // the later GroupedMessages field holds the forwarding selection instead.
    val group = bridge.getMethodData(options)!!.usingFields.first {
        it.field.className == chat && it.field.typeName == "$message\$GroupedMessages"
    }.field
    fun invoked(result: String) = forward.invokes.distinctBy { it.descriptor }.single {
        it.className == chat && it.paramCount == 0 && it.returnTypeName == result
    }.descriptor
    val sticker = bridge.findMethod { matcher {
        declaredClass(helper); name("sendSticker"); returnType("void"); paramCount(17)
    } }.single { it.paramTypeNames.take(6) == listOf("org.telegram.tgnet.TLRPC\$Document", "java.lang.String", "long",
        message, message, "org.telegram.tgnet.tl.TL_stories\$StoryItem") }
    check(sticker.paramTypeNames.drop(7) == listOf("$message\$SendAnimationData", "boolean", "int", "int", "boolean",
        "java.lang.Object", chatArgs, "long", "long", suggestion))
    val controller = "org.telegram.messenger.MessagesController"
    fun restriction(name: String, argument: String) = bridge.findMethod { matcher {
        declaredClass(controller); name(name); paramTypes(argument); returnType("boolean")
    } }.single().descriptor
    return mapOf(
        "forward" to forward.descriptor,
        "slowMode" to invoked("boolean"),
        "monoPeer" to invoked("long"),
        "dialog" to forwardField("long"),
        "thread" to forwardField(message),
        "suggestion" to forwardField(suggestion),
        "group" to group.descriptor,
        "currentChat" to bridge.findField { matcher {
            declaredClass(chat); type("org.telegram.tgnet.TLRPC\$Chat"); addReadMethod { descriptor(createMenu) }
        } }.single().descriptor,
        "chatArgs" to bridge.findMethod { matcher { declaredClass(chat); paramTypes(); returnType(chatArgs) } }.single().descriptor,
        "sendSticker" to sticker.descriptor,
        "sendText" to bridge.findMethod { matcher { declaredClass(helper); name("sendMessage"); paramTypes(params); returnType("void") } }.single().descriptor,
        "textParams" to bridge.findMethod { matcher { declaredClass(params); name("of"); paramTypes("java.lang.String", "long"); returnType(params) } }.single().descriptor,
        "chatNoForwards" to restriction("isChatNoForwards", "org.telegram.tgnet.TLRPC\$Chat"),
        "userNoForwards" to restriction("isUserNoForwards", "long")
    )
}
