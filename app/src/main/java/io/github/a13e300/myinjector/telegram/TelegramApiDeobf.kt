package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.ClassData
import org.luckypray.dexkit.result.MethodData
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
        "AndroidUtilities.isMapsInstalled" -> candidates.single {
            it.returnTypeName == "boolean" && it.invokes.any { call -> call.className == "android.content.pm.PackageManager" && call.name == "getApplicationInfo" }
        }
        "DownloadController.hasUnviewedDownloads" -> candidates.single { !Modifier.isStatic(it.modifiers) && it.returnTypeName == "boolean" }
        else -> error("No Telegram API method rule for $owner.$name($expected)")
    }
    return found
}
