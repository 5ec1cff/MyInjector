package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

internal fun findProfileMenu(bridge: DexKitBridge): Map<String, String> {
    val menu = bridge.findMethod { matcher {
        paramTypes("boolean"); returnType("void")
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("ViewDiscussion") }
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("SearchMembers") }
    } }.single()
    val code = scanTelegramDex(bridge, menu)
    val discussion = code.calls.single { call ->
        call.args.any { it?.resource == "ViewDiscussion" } && call.receiver?.field?.className == menu.className
    }
    check(discussion.method.paramTypeNames.take(2) == listOf("int", "int") && discussion.method.paramCount == 3)
    fun field(type: String) = bridge.findField { matcher { declaredClass(menu.className); type(type) } }.single().descriptor
    return mapOf(
        "menu" to menu.descriptor,
        "otherItem" to discussion.receiver!!.field!!.descriptor,
        "addSubItem" to discussion.method.descriptor,
        "discussionId" to discussion.args[0]!!.number!!.toString(),
        "currentChat" to field("org.telegram.tgnet.TLRPC\$Chat"),
        "chatInfo" to field("org.telegram.tgnet.TLRPC\$ChatFull"),
    )
}

internal fun profileMenuMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { findProfileMenu(creator.bridge) }
    return listOf("menu", "otherItem", "addSubItem", "discussionId", "currentChat", "chatInfo").associateWith { key ->
        creator.create("ProfileMenu.$key") {
            val descriptor = found.getValue(key)
            when (key) {
                "discussionId" -> ObfsInfo("", descriptor)
                "otherItem", "currentChat", "chatInfo" -> {
                    val field = DexField(descriptor)
                    ObfsInfo(field.className, field.name, descriptor)
                }
                else -> {
                    val method = DexMethod(descriptor)
                    ObfsInfo(method.className, method.name, descriptor)
                }
            }
        }
    }
}
