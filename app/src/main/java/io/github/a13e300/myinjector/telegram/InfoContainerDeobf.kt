package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

internal fun findInfoContainer(bridge: DexKitBridge, profileMenu: String): Map<String, String> {
    val found = linkedMapOf<String, String>()
    val menu = bridge.getMethodData(profileMenu)!!
    val profileCreate = bridge.findMethod { matcher {
        declaredClass(menu.className); paramTypes(); returnType("boolean")
        usingEqStrings("user_id", "chat_id", "topic_id", "my_profile")
    } }.single()
    found["profileTopic"] = scanTelegramDex(bridge, profileCreate).writes.single {
        it.field.className == menu.className && it.value?.bundleKey == "topic_id"
    }.field.descriptor
    val profileCode = scanTelegramDex(bridge, menu)
    val editVisible = profileCode.writes.first { it.field.className == menu.className && it.field.typeName == "boolean" }.field
    found["editVisible"] = editVisible.descriptor
    val editItem = profileCode.calls.filter { it.method.name == "setVisibility" && it.receiver?.field?.className == menu.className }
        .filter { call -> profileCode.compares.lastOrNull { cmp -> cmp.offset < call.offset && cmp.fields.any { it.typeName == "boolean" } }
            ?.fields?.any { it.descriptor == editVisible.descriptor } == true }
        .map { it.receiver!!.field!! }.distinctBy { it.descriptor }.single()
    found["editItem"] = editItem.descriptor
    found["setIcon"] = bridge.findMethod { matcher { declaredClass(editItem.typeName); name("setIcon"); paramTypes("int"); returnType("void") } }.single().descriptor

    val update = bridge.findMethod { matcher {
        paramTypes("boolean", "boolean"); returnType("void")
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("ChannelAdministrators") }
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("ChannelBlacklist") }
    } }.single()
    found["updateFields"] = update.descriptor
    val edit = update.className
    val create = bridge.findMethod { matcher { declaredClass(edit); name("createView"); paramTypes("android.content.Context"); returnType("android.view.View") } }.single()
    found["editCreate"] = create.descriptor
    val updateCode = scanTelegramDex(bridge, update)
    val createCode = scanTelegramDex(bridge, create)
    fun cell(resource: String, code: TgDexCode): FieldData = code.calls.filter {
        it.receiver?.field?.className == edit && it.args.any { arg -> arg?.resource == resource }
    }.map { it.receiver!!.field!! }.distinctBy { it.descriptor }.single()
    val admin = cell("ChannelAdministrators", updateCode)
    found["adminCell"] = admin.descriptor
    found["blockCell"] = cell("ChannelBlacklist", updateCode).descriptor
    found["logCell"] = cell("EventLog", createCode).descriptor
    val hasAdmin = createCode.calls.last { it.method.className == "org.telegram.messenger.ChatObject" && it.method.name == "hasAdminRights" }
    val hidden = createCode.calls.filter { call ->
        call.offset > hasAdmin.offset && call.method.name == "setVisibility" && call.args.singleOrNull()?.number == 8 && call.receiver?.field?.className == edit
    }.take(2).map { it.receiver!!.field!! }
    check(hidden.size == 2 && hidden.count { it.typeName == "android.widget.LinearLayout" } == 1)
    found["infoContainer"] = hidden.single { it.typeName == "android.widget.LinearLayout" }.descriptor
    found["settingsTopSection"] = hidden.single { it.typeName != "android.widget.LinearLayout" }.descriptor
    found["editChat"] = bridge.findField { matcher { declaredClass(edit); type("org.telegram.tgnet.TLRPC\$Chat") } }.single().descriptor
    // Reuse the native TextCell overload. R8 inserts/reorders the animated flag.
    found["setAdminText"] = updateCode.calls.first { it.receiver?.field?.descriptor == admin.descriptor && it.args.any { arg -> arg?.resource == "ChannelAdministrators" } }.method.descriptor

    val usersCreate = bridge.findMethod { matcher {
        name("createView"); paramTypes("android.content.Context"); returnType("android.view.View")
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("ChannelSearchException") }
    } }.single()
    found["usersCreate"] = usersCreate.descriptor
    val users = usersCreate.className
    val usersCreateCode = scanTelegramDex(bridge, usersCreate)
    for ((key, resource) in mapOf("searchItem" to "outline_header_search", "doneItem" to "ic_ab_done")) {
        found[key] = usersCreateCode.writes.filter { it.field.className == users && it.value?.resource == resource }
            .map { it.field }.distinctBy { it.descriptor }.single().descriptor
    }
    val constructor = bridge.findMethod { matcher { declaredClass(users); name("<init>"); paramTypes("android.os.Bundle") } }.single()
    found["usersType"] = scanTelegramDex(bridge, constructor).writes.single { it.field.className == users && it.value?.bundleKey == "type" }.field.descriptor
    found["usersChat"] = bridge.findField { matcher { declaredClass(users); type("org.telegram.tgnet.TLRPC\$Chat") } }.single().descriptor
    val rows = bridge.findMethod { matcher { declaredClass(users); paramTypes(); returnType("void") } }
        .single { method -> method.usingFields.count { it.usingType == FieldUsingType.Write && it.field.className == users && it.field.typeName == "int" } > 50 }
    found["updateRows"] = rows.descriptor
    val reset = scanTelegramDex(bridge, rows).writes.filter { it.field.className == users && it.field.typeName == "int" }
    val allRows = reset.filter { it.value?.number == -1 }.map { it.field }.distinctBy { it.descriptor }
    check(allRows.size > 50)
    val count = reset.first { it.value?.number == 0 }.field
    found["rowCount"] = count.descriptor
    found["rowFields"] = allRows.joinToString("\n") { it.descriptor }
    // Recent actions was removed from the newer adapter, but its reset field
    // still precedes the anti-spam rows. Disabled native rows need no shifting.
    found["recentActionsRow"] = allRows.first().descriptor
    val viewType = bridge.findMethod { matcher { paramTypes("int"); returnType("int") } }.single { method ->
        method.usingFields.map { it.field.descriptor }.intersect(allRows.map { it.descriptor }.toSet()).size > 30
    }
    val viewCode = scanTelegramDex(bridge, viewType)
    // getItemViewType's three OR comparisons jump to the native separator
    // type (3): addNewSectionRow, participantsDividerRow, divider2 in order.
    val separatorReturn = viewCode.returns.single { it.value?.number == 3 }
    fun fallthroughJump(offset: Int): Int? {
        val code = viewType.insns
        val pos = offset + 2 // if-* occupies two code units
        return when (code[pos].code and 0xff) {
            0x28 -> pos + (code[pos].code ushr 8).toByte()
            0x29 -> pos + code[pos + 1].code.toShort()
            0x2a -> pos + (code[pos + 1].code or (code[pos + 2].code shl 16))
            else -> null
        }
    }
    val separators = viewCode.compares.filter {
        it.target == separatorReturn.offset - 1 || fallthroughJump(it.offset) == separatorReturn.offset - 1
    }
        .flatMap { it.fields }.filter { it.className == users }.distinctBy { it.descriptor }
    check(separators.size == 3)
    found["addNewSectionRow"] = separators.first().descriptor
    found["participantsDivider2Row"] = separators.last().descriptor
    val removedReturn = viewCode.returns.single { it.value?.number == 6 }
    found["removedUsersRow"] = viewCode.compares.last { it.offset < removedReturn.offset }
        .fields.single { it.className == users }.descriptor
    return found
}

internal val infoContainerMethodKeys = listOf("setIcon", "updateFields", "editCreate", "setAdminText", "usersCreate", "updateRows")
private val infoContainerKeys = infoContainerMethodKeys + listOf("profileTopic", "editVisible", "editItem", "adminCell", "blockCell", "logCell",
    "infoContainer", "settingsTopSection", "editChat", "searchItem", "doneItem", "usersType", "usersChat", "rowCount", "rowFields",
    "recentActionsRow", "addNewSectionRow", "participantsDivider2Row", "removedUsersRow")

internal fun infoContainerMembers(creator: ObfsTableCreator, menu: String): Map<String, ObfsInfo> {
    val found by lazy { findInfoContainer(creator.bridge, menu) }
    return infoContainerKeys.associateWith { key -> creator.create("InfoContainer.$key") {
        val descriptor = found.getValue(key)
        when {
            key == "rowFields" -> ObfsInfo("", "", descriptor)
            key in infoContainerMethodKeys -> {
                val method = DexMethod(descriptor)
                ObfsInfo(method.className, method.name, descriptor)
            }
            else -> {
                val field = DexField(descriptor)
                ObfsInfo(field.className, field.name, descriptor)
            }
        }
    } }
}
