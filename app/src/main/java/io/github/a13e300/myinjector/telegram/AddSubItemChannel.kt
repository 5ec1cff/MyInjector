package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.callS
import io.github.a13e300.myinjector.arch.getObj
import io.github.a13e300.myinjector.arch.getObjSAs
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.logE
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

// Feature ported from https://github.com/cinit/TMoe (AddSubItemChannel).
class AddSubItemChannel : MyDynHook("addSubItemChannel") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.addSubItemChannel

    override fun onHook() {
        val members = profileMenuMembers(TelegramHandler.creator)
        fun field(key: String) = DexField(members.getValue(key).descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        fun method(key: String) = DexMethod(members.getValue(key).descriptor)
            .getMethodInstance(classLoader).apply { isAccessible = true }
        val chat = field("currentChat")
        val info = field("chatInfo")
        val other = field("otherItem")
        val add = method("addSubItem")
        val discussionId = members.getValue("discussionId").memberName.toInt()
        val chatObject = findClass("org.telegram.messenger.ChatObject")
        val icon = findClass("org.telegram.messenger.R\$drawable").getObjSAs<Int>("msg_channel")
        method("menu").hookAfter(cond = ::isEnabled) { param ->
            if (param.throwable != null) return@hookAfter
            runCatching {
                val currentChat = chat.get(param.thisObject) ?: return@runCatching
                if (currentChat.getObj("megagroup") != true || chatObject.callS("isChannel", currentChat) != true) return@runCatching
                val full = info.get(param.thisObject) ?: return@runCatching
                if ((full.getObj("linked_chat_id") as Number).toLong() == 0L) return@runCatching
                val item = other.get(param.thisObject) ?: return@runCatching
                // Reuse the native discussion action: it opens linked_chat_id
                // in either direction and the native menu is rebuilt each time.
                add.invoke(item, discussionId, icon, "打开频道")
            }.onFailure { logE("AddSubItemChannel failed", it); onHookError(it) }
        }
    }
}
