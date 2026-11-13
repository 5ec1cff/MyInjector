package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.query.enums.StringMatchType
import java.lang.reflect.Modifier
import java.util.WeakHashMap

private val telegramApiClasses = WeakHashMap<DexKitBridge, MutableMap<String, String>>()

// Telegram 13.2 also renames messenger classes. BaseFragment's public getters
// still identify account services without creating their instances.
@Synchronized
internal fun telegramApiClass(bridge: DexKitBridge, name: String): String {
    bridge.getClassData(name)?.let { return name }
    val cache = telegramApiClasses.getOrPut(bridge) { mutableMapOf() }
    cache[name]?.let { return it }
    if (name == "org.telegram.ui.ActionBar.BaseFragment") {
        return bridge.findClass { matcher { addField { name("fragmentView"); type("android.view.View") } } }
            .single().name.also { cache[name] = it }
    }
    fun apiType(logical: String) = bridge.getClassData(telegramApiClass(bridge, "org.telegram.messenger.$logical"))!!
    fun strings(vararg values: String): ClassData = bridge.findClass {
        matcher { usingStrings(values.toList(), StringMatchType.Equals) }
    }.single { it.name.startsWith("org.telegram.messenger.") }
    val factories = mapOf(
        "AccountInstance" to "getAccountInstance", "DownloadController" to "getDownloadController",
        "FileLoader" to "getFileLoader", "MediaDataController" to "getMediaDataController",
        "MessagesController" to "getMessagesController", "MessagesStorage" to "getMessagesStorage",
        "NotificationCenter" to "getNotificationCenter", "SendMessagesHelper" to "getSendMessagesHelper",
        "UserConfig" to "getUserConfig",
    )
    val simple = name.removePrefix("org.telegram.messenger.")
    val found = if (simple in factories) {
        val base = bridge.getClassData("org.telegram.ui.ActionBar.BaseFragment") ?: bridge.findClass {
            matcher { addField { name("fragmentView"); type("android.view.View") } }
        }.single()
        base.methods.single { it.name == factories.getValue(simple) && it.paramCount == 0 }.returnType!!
    } else when (simple) {
        "AndroidUtilities" -> strings("%d GB")
        "ChatObject" -> strings("AttachPhotoRestricted", "AttachVideoRestricted")
        "Emoji" -> strings("emojis2")
        "EmojiData" -> strings("☺", "☕")
        "Emoji\$SimpleEmojiDrawable" -> bridge.findClass { matcher {
            addField { type("android.graphics.Rect") }
            addField { type("android.graphics.Paint") }
            addField { type("boolean") }
            addMethod { name("draw"); paramTypes("android.graphics.Canvas") }
        } }.single { candidate ->
            candidate.superClass?.name?.let { parent ->
                apiType("Emoji").methods.any { it.returnTypeName == parent }
            } == true && candidate.fields.size == 4
        }
        "ImageLocation" -> strings("[richmedia] strippedKey=")
        "LocaleController" -> strings("YesterdayAtFormatted")
        "MediaController\$PhotoEntry" -> strings("MotionPhotoXmpParser")
        "MediaController\$MediaEditState" -> apiType("MediaController\$PhotoEntry").superClass!!
        "MessageObject" -> bridge.findMethod { matcher {
            name("<init>")
            paramTypes("int", "org.telegram.tgnet.TLRPC\$Message", "boolean", "boolean")
        } }.single().declaredClass!!
        "MessageSuggestionParams" -> bridge.findMethod { matcher {
            paramTypes("org.telegram.tgnet.TLRPC\$SuggestedPost")
        } }.single { it.returnTypeName == it.className }.declaredClass!!
        "SendMessageChatArguments" -> bridge.findClass { matcher {
            addField { type("java.lang.String") }
            addField { type("int") }
            addField { type("long") }
        } }.single { candidate ->
            val fields = candidate.fields.filter { !Modifier.isStatic(it.modifiers) }
            candidate.name.startsWith("org.telegram.messenger.") &&
                fields.map { it.typeName }.sorted() == listOf("int", "java.lang.String", "long") &&
                candidate.fields.any { Modifier.isStatic(it.modifiers) && it.typeName == candidate.name }
        }
        "UserObject" -> strings("agent").also { candidate ->
            check(candidate.methods.any { it.paramTypeNames == listOf("org.telegram.tgnet.TLRPC\$User") })
        }
        "SendMessagesHelper\$SendMessageParams" -> bridge.findMethod { matcher {
            paramCount(2)
        } }.single { it.paramTypeNames.sorted() == listOf("java.lang.String", "long") && it.returnTypeName == it.className && it.declaredClass!!.fields.size > 30 &&
            apiType("SendMessagesHelper").methods.any { send -> send.paramTypeNames == listOf(it.className) && send.returnTypeName == "void" } }.declaredClass!!
        else -> error("No Telegram API class rule for $name")
    }
    return found.name.also { cache[name] = it }
}

internal fun telegramApiMethod(bridge: DexKitBridge, owner: String, name: String, vararg params: String): MethodData {
    val cls = bridge.getClassData(telegramApiClass(bridge, owner))!!
    val expected = params.map { if (it == "org.telegram.ui.ActionBar.BaseFragment" || it.startsWith("org.telegram.messenger.") && !it.startsWith("org.telegram.messenger.R\$")) telegramApiClass(bridge, it) else it }
    cls.methods.singleOrNull { it.name == name && it.paramTypeNames == expected }?.let { return it }
    val candidates = cls.methods.filter { it.paramTypeNames.sorted() == expected.sorted() }
    fun stringResult(method: MethodData) = Modifier.isStatic(method.modifiers) && method.returnTypeName == "java.lang.String"
    val found = when ("${owner.substringAfterLast('.') }.$name") {
        "LocaleController.getString" -> when (expected) {
            listOf("java.lang.String", "int") -> {
                val keyOnly = telegramApiMethod(bridge, owner, name, "java.lang.String")
                candidates.single { stringResult(it) && keyOnly.invokes.any { call -> call.descriptor == it.descriptor } }
            }
            listOf("java.lang.String") -> candidates.single { stringResult(it) && "LOC_ERR:" in it.usingStrings }
            listOf("int") -> {
                val full = telegramApiMethod(bridge, owner, name, "java.lang.String", "int")
                candidates.single { stringResult(it) && it.invokes.any { call -> call.descriptor == full.descriptor } }
            }
            else -> error("Unsupported LocaleController.getString signature: $expected")
        }
        "LocaleController.formatDateOnline", "LocaleController.formatShortNumber" -> candidates.single(::stringResult)
        "AndroidUtilities.checkAndroidTheme" -> candidates.single { Modifier.isStatic(it.modifiers) && it.returnTypeName == "void" }
        "AndroidUtilities.lerp" -> candidates.single { Modifier.isStatic(it.modifiers) && it.returnTypeName == "float" }
        "AndroidUtilities.dpf2" -> candidates.single {
            it.returnTypeName == "float" && it.invokes.isEmpty() && it.opCodes.any { opcode -> opcode in listOf(0xa8, 0xc8) }
        }
        "AndroidUtilities.dp" -> candidates.single {
            it.returnTypeName == "int" && it.invokes.any { call -> call.className == "java.lang.Math" && call.name == "ceil" }
        }
        "AndroidUtilities.isMapsInstalled" -> candidates.single {
            it.returnTypeName == "boolean" && it.invokes.any { call -> call.className == "android.content.pm.PackageManager" && call.name == "getApplicationInfo" }
        }
        "DownloadController.hasUnviewedDownloads" -> candidates.single { !Modifier.isStatic(it.modifiers) && it.returnTypeName == "boolean" }
        "UserObject.getFirstName" -> candidates.single(::stringResult)
        "ChatObject.isChannel" -> candidates.single {
            // Tests channel subclasses, then delegates the community check.
            it.returnTypeName == "boolean" && it.usingFields.isEmpty() && it.opCodes.count { opcode -> opcode == 0x20 } == 2 && it.invokes.size == 1
        }
        "ChatObject.canSendMessages" -> candidates.single { it.returnTypeName == "boolean" && it.usingFields.any { f -> f.field.name == "join_to_send" } }
        "ChatObject.isForum", "ChatObject.isMonoForum" -> candidates.single {
            it.returnTypeName == "boolean" && it.usingFields.size == 1 && it.usingFields.single().field.name == if (name == "isForum") "forum" else "monoforum"
        }
        "ChatObject.canWriteToChat" -> candidates.single { it.returnTypeName == "boolean" && it.usingFields.any { f -> f.field.name == "gigagroup" } && it.usingFields.any { f -> f.field.name == "broadcast" } }
        "ChatObject.isNotInChat" -> {
            val send = telegramApiMethod(bridge, owner, "canSendMessages", "org.telegram.tgnet.TLRPC\$Chat")
            candidates.single { method -> method.returnTypeName == "boolean" && method.usingFields.any { it.field.name == "left" } && send.invokes.any { it.descriptor == method.descriptor } }
        }
        "ChatObject.canManageMonoForum" -> candidates.single { it.returnTypeName == "boolean" && it.usingFields.isEmpty() && it.invokes.size == 1 }
        "ChatObject.canManageTopic" -> candidates.single { it.returnTypeName == "boolean" && it.usingFields.isEmpty() && it.invokes.size == 2 }
        "ChatObject.canUserDoAction" -> candidates.single {
            it.returnTypeName == "boolean" && it.usingFields.any { f -> f.field.name == "default_banned_rights" } && it.usingFields.any { f -> f.field.name == "admin_rights" }
        }
        "ChatObject.canSendPhoto", "ChatObject.canSendVideo", "ChatObject.canSendMusic", "ChatObject.canSendDocument", "ChatObject.canSendVoice", "ChatObject.canSendRoundVideo", "ChatObject.canSendStickers" -> {
            val action = mapOf("canSendPhoto" to 16, "canSendVideo" to 17, "canSendMusic" to 18, "canSendDocument" to 19, "canSendVoice" to 20, "canSendRoundVideo" to 21, "canSendStickers" to 8).getValue(name)
            val check = telegramApiMethod(bridge, owner, "canUserDoAction", "org.telegram.tgnet.TLRPC\$Chat", "int")
            candidates.single { it.returnTypeName == "boolean" && scanTelegramDex(bridge, it).calls.any { call ->
                call.method.descriptor == check.descriptor && call.args[check.paramTypeNames.indexOf("int")]?.number == action
            } }
        }
        "UserObject.isDeleted" -> candidates.single { it.returnTypeName == "boolean" && it.usingFields.any { f -> f.field.name == "deleted" } }
        "UserObject.isReplyUser" -> bridge.findMethod { matcher { declaredClass(cls.name); paramTypes(expected); returnType("boolean"); usingNumbers(708513L) } }.single()
        "UserObject.isBotForum" -> candidates.single { it.returnTypeName == "boolean" && it.usingFields.size == 1 && it.usingFields.single().field.name == "bot_forum_view" }
        "MessagesController.getUser" -> candidates.single { it.returnTypeName == "org.telegram.tgnet.TLRPC\$User" }
        "MessagesController.getChat" -> candidates.single { it.returnTypeName == "org.telegram.tgnet.TLRPC\$Chat" }
        "MessagesController.getInputPeer" -> candidates.single { it.returnTypeName == "org.telegram.tgnet.TLRPC\$InputPeer" }
        "MessagesController.getInputUser" -> candidates.single { it.returnTypeName == "org.telegram.tgnet.TLRPC\$InputUser" }
        "FileLoader.getPathToMessage" -> candidates.filter { it.returnTypeName == "java.io.File" }.let { methods ->
            methods.singleOrNull { !Modifier.isStatic(it.modifiers) } ?: methods.first()
        }
        "MessagesController.openChatOrProfileWith" -> candidates.single { it.returnTypeName == "void" &&
            expected.toSet() == setOf("org.telegram.tgnet.TLRPC\$User", "org.telegram.tgnet.TLRPC\$Chat", telegramApiClass(bridge, "org.telegram.ui.ActionBar.BaseFragment"), "int") }
        "MessagesController.putUsers", "MessagesController.putChats" -> {
            val tlType = if (name == "putUsers") "org.telegram.tgnet.TLRPC\$User" else "org.telegram.tgnet.TLRPC\$Chat"
            candidates.single { it.returnTypeName == "void" && it.invokes.any { call -> tlType in call.paramTypeNames } }
        }
        "MessagesStorage.isMonoForum" -> candidates.single { method ->
            method.returnTypeName == "boolean" && scanTelegramDex(bridge, method).calls.any { call ->
                call.method.className == cls.name && call.method.returnTypeName == "boolean" &&
                    call.method.paramTypeNames.sorted() == listOf("int", "long") &&
                    call.args[call.method.paramTypeNames.indexOf("int")]?.number == 4
            }
        }
        "MessagesController.getDialogs" -> candidates.single { it.returnTypeName == "java.util.ArrayList" }
        "MessagesController.removeFolder" -> candidates.single {
            it.returnTypeName == "void" && it.invokes.any { call -> call.className == "java.util.ArrayList" && call.name == "remove" }
        }
        "MessagesController.isChatNoForwards" -> candidates.single {
            it.returnTypeName == "boolean" && (expected != listOf("long") ||
                it.invokes.any { call -> call.returnTypeName == "org.telegram.tgnet.TLRPC\$UserFull" } &&
                it.invokes.any { call -> call.returnTypeName == "org.telegram.tgnet.TLRPC\$Chat" })
        }
        "MessagesController.isUserNoForwards" -> candidates.single {
            it.returnTypeName == "boolean" && expected == listOf("long") &&
                it.invokes.any { call -> call.returnTypeName == "org.telegram.tgnet.TLRPC\$UserFull" }
        }
        "UserConfig.getCurrentUser" -> candidates.single { it.returnTypeName == "org.telegram.tgnet.TLRPC\$User" }
        "UserConfig.getClientUserId" -> candidates.single { it.returnTypeName == "long" && !Modifier.isStatic(it.modifiers) }
        "MessageObject.getId" -> candidates.single {
            it.returnTypeName == "int" && it.usingFields.any { field -> field.field.descriptor == "Lorg/telegram/tgnet/TLRPC\$Message;->ephemeralAnchorMsgId:I" }
        }
        "MessageObject.getPeerId" -> candidates.single { it.returnTypeName == "long" }
        "MessageObject.getFromChatId" -> candidates.single {
            it.returnTypeName == "long" && it.usingFields.size == 1 &&
                it.usingFields.single().field.descriptor == "Lorg/telegram/tgnet/TLRPC\$Message;->from_id:Lorg/telegram/tgnet/TLRPC\$Peer;"
        }
        "MessageObject.getDialogId" -> if (expected.isNotEmpty()) candidates.single {
            it.returnTypeName == "long" && it.usingFields.any { field -> field.field.descriptor == "Lorg/telegram/tgnet/TLRPC\$Message;->dialog_id:J" }
        } else {
            val raw = telegramApiMethod(bridge, owner, name, "org.telegram.tgnet.TLRPC\$Message")
            candidates.single { it.invokes.size == 1 && it.invokes.single().descriptor == raw.descriptor }
        }
        "MessageObject.needDrawBluredPreview" -> candidates.single {
            !Modifier.isStatic(it.modifiers) && it.returnTypeName == "boolean" &&
                it.usingFields.any { field -> field.field.typeName == "org.telegram.tgnet.TLRPC\$Message" } &&
                it.invokes.any { call -> call.descriptor == "Ljava/lang/Math;->max(II)I" }
        }
        "MessageObject.setQuery" -> candidates.single { it.returnTypeName == "void" }
        "MediaController\$MediaEditState.reset" -> candidates.single { it.returnTypeName == "void" }
        "MediaController\$MediaEditState.isHighQuality" -> candidates.single {
            it.returnTypeName == "boolean" && it.invokes.any { call -> call.descriptor == "Ljava/lang/Boolean;->booleanValue()Z" }
        }
        "AndroidUtilities.shouldShowUrlInAlert" -> candidates.single {
            it.returnTypeName == "boolean" && it.invokes.any { call -> call.className == "android.net.Uri" && call.name == "getHost" }
        }
        else -> error("No Telegram API method rule for $owner.$name($expected)")
    }
    return found
}

internal fun telegramSearchMethod(bridge: DexKitBridge, name: String): MethodData {
    val media = bridge.getClassData(telegramApiClass(bridge, "org.telegram.messenger.MediaDataController"))!!
    media.methods.singleOrNull { it.name == name && (name != "searchMessagesInChat" || it.paramCount >= 11) }?.let { return it }
    val methods = media.methods
    return when (name) {
        "searchMessagesInChat" -> methods.single {
            it.paramCount >= 11 && it.paramTypeNames.take(7) == listOf("java.lang.String", "long", "long", "int", "int", "long", "boolean")
        }
        "searchEndReached" -> methods.single { it.paramCount == 0 && it.returnTypeName == "boolean" && it.invokes.isEmpty() && it.usingFields.any { f -> f.field.typeName == "boolean[]" } }
        "getMask" -> methods.single { it.paramCount == 0 && it.returnTypeName == "int" && it.usingFields.any { f -> f.field.typeName == "boolean[]" } }
        "getSearchCount" -> methods.single { it.paramCount == 0 && it.returnTypeName == "int" && it.invokes.any { call -> call.descriptor == "Ljava/lang/Math;->max(II)I" } && it.usingFields.any { f -> f.field.typeName == "int[]" } }
        "loadMoreSearchMessages" -> {
            val search = telegramSearchMethod(bridge, "searchMessagesInChat")
            methods.single { it.returnTypeName == "void" && it.paramTypeNames in listOf(emptyList(), listOf("boolean")) && it.invokes.any { call -> call.descriptor == search.descriptor } }
        }
        "clearFoundMessageObjects" -> methods.single { it.paramCount == 0 && it.returnTypeName == "void" &&
            it.invokes.count { call -> call.descriptor == "Ljava/util/ArrayList;->clear()V" } >= 3 &&
            it.usingFields.count { f -> f.field.typeName == "java.util.ArrayList" } >= 3 }
        else -> error("Missing search API rule: $name")
    }
}

internal fun telegramApiField(bridge: DexKitBridge, owner: String, name: String): FieldData {
    val cls = bridge.getClassData(telegramApiClass(bridge, owner))!!
    cls.fields.singleOrNull { it.name == name }?.let { return it }
    return when ("${owner.substringAfterLast('.') }.$name") {
        "MessageObject.messageOwner" -> cls.fields.single { it.typeName == "org.telegram.tgnet.TLRPC\$Message" }
        "MediaController\$MediaEditState.highQuality" -> cls.fields.single { it.typeName == "java.lang.Boolean" }
        "MediaController\$MediaEditState.isVideo" -> {
            val entry = bridge.getClassData(telegramApiClass(bridge, "org.telegram.messenger.MediaController\$PhotoEntry"))!!
            entry.methods.filter { it.isConstructor }.flatMap { it.usingFields }.map { it.field }.distinctBy { it.descriptor }
                .single { it.className == cls.name && it.typeName == "boolean" }
        }
        "MessagesController.blockePeers" -> {
            cls.fields.single { field ->
                field.typeName == "org.telegram.messenger.support.LongSparseIntArray" &&
                    cls.methods.any { method -> method.usingFields.any { it.field.descriptor == field.descriptor } &&
                        method.invokes.any { it.name == "indexOfKey" && it.paramTypeNames == listOf("long") } &&
                        method.invokes.any { it.className == "org.telegram.tgnet.TLRPC\$TL_contacts_block" } }
            }
        }
        else -> error("No Telegram API field rule for $owner.$name")
    }
}
