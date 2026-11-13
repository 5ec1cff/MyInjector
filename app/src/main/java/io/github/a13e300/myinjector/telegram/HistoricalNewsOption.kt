package io.github.a13e300.myinjector.telegram

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObj
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

// Ported from TMoe's HistoricalNewsOption; repeating messages has its own hook.
// https://github.com/cinit/TMoe
class HistoricalNewsOption : MyDynHook("historicalNewsOption") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.historicalNewsOption

    override fun onHook() {
        val chatClass = MessageMenuHook.initialize(classLoader)
        val members = historyMembers(TelegramHandler.creator, chatClass.name)
        val methods = members.mapValues {
            DexMethod(it.value.descriptor).getMethodInstance(classLoader).apply { isAccessible = true }
        }
        val searchSender = methods.getValue("searchSender")
        val anchoredSearch = AnchoredMessageSearch(classLoader, chatClass, ::isEnabled, ::onHookError,
            loadPackageParam.packageName in setOf("org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta"))
        fun invoke(key: String, chat: Any, vararg args: Any?) {
            val method = methods.getValue(key)
            if (Modifier.isStatic(method.modifiers)) method.invoke(null, chat, *args)
            else method.invoke(chat, *args)
        }
        // R8 can inline getFromPeer. Read messageOwner.from_id directly.
        val messageOwner = telegramApiField(classLoader, "org.telegram.messenger.MessageObject", "messageOwner")
        MessageMenuHook.register("historicalNewsOption", "查看历史消息", "msg_search", ::isEnabled,
            { onHookError(it) }) { chat, message ->
            val peer = messageOwner.get(message)?.getObj("from_id") ?: return@register null
            val userId = (peer.getObj("user_id") as Number).toLong()
            val channelId = (peer.getObj("channel_id") as Number).toLong()
            val groupId = (peer.getObj("chat_id") as Number).toLong()
            val id = when {
                userId != 0L -> userId
                channelId != 0L -> channelId
                groupId != 0L -> groupId
                else -> return@register null
            }
            MessageMenuAction(
                click = { view ->
                    val controller = chat.call("getMessagesController")!!
                    val sender = methods.getValue(if (userId != 0L) "getUser" else "getChat").invoke(controller, id)
                    if (sender == null) {
                        Toast.makeText(view.context, "发送者资料尚未加载", Toast.LENGTH_SHORT).show()
                    } else {
                        MessageMenuHook.dismiss(chat)
                        invoke("openSearch", chat, "")
                        // R8 swaps Chat/User parameter order; use the resolved types.
                        val args = searchSender.parameterTypes.filter { it != chatClass }.map {
                            if (it.isInstance(sender)) sender else null
                        }.toTypedArray()
                        anchoredSearch.fromMessage(chat, message) { invoke("searchSender", chat, *args) }
                        invoke("showSearchList", chat, true)
                    }
                },
                longClick = { view ->
                    view.context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("发送者 ID", id.toString()))
                    Toast.makeText(view.context, "ID: $id", Toast.LENGTH_SHORT).show()
                    MessageMenuHook.dismiss(chat)
                },
            )
        }
    }
}

internal fun findHistoricalNews(bridge: DexKitBridge, chat: String): Map<String, String> {
    val sender = bridge.findMethod { matcher {
        declaredClass(chat); returnType("void")
        usingTelegramResource(bridge, "string", "SearchFrom")
    } }.single { it.paramTypeNames.toSet() == setOf("org.telegram.tgnet.TLRPC\$User", "org.telegram.tgnet.TLRPC\$Chat") }
    val search = telegramSearchMethod(bridge, "searchMessagesInChat")
    val open = bridge.findMethod { matcher {
        declaredClass(chat); paramTypes("java.lang.String"); returnType("void")
        addInvoke { descriptor(search.descriptor) }
    } }.single()
    val show = bridge.findMethod { matcher {
        declaredClass(chat); paramTypes("boolean"); returnType("void")
        usingTelegramResource(bridge, "string", "SearchAsList")
    } }.single()
    fun lookup(name: String) = telegramApiMethod(bridge, "org.telegram.messenger.MessagesController", name, "java.lang.Long")
    return mapOf("searchSender" to sender.descriptor, "openSearch" to open.descriptor, "showSearchList" to show.descriptor,
        "getUser" to lookup("getUser").descriptor,
        "getChat" to lookup("getChat").descriptor)
}

private fun historyMembers(creator: ObfsTableCreator, chat: String): Map<String, ObfsInfo> {
    val found by lazy { findHistoricalNews(creator.bridge, chat) }
    return listOf("searchSender", "openSearch", "showSearchList", "getUser", "getChat").associateWith { key ->
        creator.create("HistoricalNews.$key") {
            val descriptor = found.getValue(key)
            val method = DexMethod(descriptor)
            ObfsInfo(method.className, method.name, descriptor)
        }
    }
}
