package io.github.a13e300.myinjector.telegram

import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.currentApplication
import io.github.a13e300.myinjector.arch.hookAll
import io.github.a13e300.myinjector.arch.hookAllAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.toObfsInfo

class AntiAntiCopy : MyDynHook("antiAntiCopy") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.antiAntiCopy

    var isNoForw = false
    var isOF = true

    private fun deobf(creator: ObfsTableCreator): ObfsTable {

        val openForward = creator.create("ChatActivityOpenForward") { bridge ->
            bridge.findMethod {
                matcher {
                    usingEqStrings(
                        "onlySelect",
                        "dialogsType",
                        "messagesCount",
                        "hasPoll",
                        "hasInvoice",
                        "canSelectTopics"
                    )
                    usingTelegramResource(bridge, "string", "ForwardsRestrictedInfoUser")
                }
            }.single().toObfsInfo()
        }

        creator.create("ChatActivityProcessSelectedOption") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass(openForward.className)
                    paramTypes("int")
                    returnType("void")
                    usingEqStrings("tel:", "canSelectTopics", "messagesCount")
                }
            }.single().toObfsInfo()
        }
        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val ChatActivityProcessSelectedOption = table["ChatActivityProcessSelectedOption"]!!
        val ChatActivityOpenForward = table["ChatActivityOpenForward"]!!

        val depth = ThreadLocal.withInitial { 0 }
        for (type in listOf("long", "org.telegram.tgnet.TLRPC\$Chat")) {
            telegramApiMethod(classLoader, "org.telegram.messenger.MessagesController", "isChatNoForwards", type)
                .hook(cond = ::isEnabled,
                    before = { depth.set(depth.get() + 1) },
                    after = {
                        val level = depth.get()
                        if (level == 1) depth.remove() else depth.set(level - 1)
                        // The dialog overload calls the chat overload. Keep the
                        // original inner result until the outer check completes.
                        if (level == 1 && it.throwable == null) {
                            isNoForw = it.result as Boolean
                            if (isOF) it.result = false
                        }
                    })
        }

        val chatActivity = findClass(ChatActivityOpenForward.className)

        chatActivity.hookAllBefore(
            ChatActivityProcessSelectedOption.memberName,
            cond = ::isEnabled
        ) {
            if (it.args[0] == 2) {
                if (isNoForw) {
                    it.result = null

                    Toast.makeText(
                        currentApplication(),
                        "禁止转发",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

        chatActivity.hookAll(
            ChatActivityOpenForward.memberName, cond = ::isEnabled,
            before = { isOF = false },
            after = { isOF = true }
        )
    }
}
