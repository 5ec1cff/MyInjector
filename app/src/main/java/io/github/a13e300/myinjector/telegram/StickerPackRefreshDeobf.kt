package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.MethodData
import java.lang.reflect.Modifier

internal fun findStickerPackRefresh(bridge: DexKitBridge): Map<String, String> {
    val input = "org.telegram.tgnet.TLRPC\$InputStickerSet"
    val full = "org.telegram.tgnet.TLRPC\$TL_messages_stickerSet"
    val request = "org.telegram.tgnet.TLRPC\$TL_messages_getStickerSet"
    val notification = telegramApiClass(bridge, "org.telegram.messenger.NotificationCenter")
    val media = bridge.getClassData(telegramApiClass(bridge, "org.telegram.messenger.MediaDataController"))!!
    val alert = bridge.findMethod { matcher {
        name("<init>")
        paramCount(6..7)
    } }.filter { it.paramTypeNames[0] == "android.content.Context" &&
        it.paramTypeNames[2] == input && it.paramTypeNames[3] == full }
        .map { it.declaredClass!! }.distinctBy { it.name }.single()
    val load = alert.methods.single {
        it.returnTypeName == "void" &&
            !Modifier.isStatic(it.modifiers) &&
            (it.paramCount == 0 || it.paramTypeNames == listOf("boolean")) &&
            it.usingFields.any { use -> use.field.className == request }
    }
    val emojiHandler = bridge.findMethod { matcher {
        usingEqStrings("/addemoji/", "/addstickers/")
        returnType("void")
    } }.single { method ->
        method.usingFields.any { it.field.typeName == "java.util.ArrayList" } &&
            method.usingFields.none { it.field.typeName == full }
    }
    val emojiFields = emojiHandler.usingFields.map { it.field }.distinctBy { it.descriptor }
    val loader = emojiFields.single { it.typeName == "java.util.ArrayList" }.declaredClass
    val loaderField = emojiFields.single {
        it.typeName == loader.name || it.type.superClass?.name == loader.name
    }
    val show = loaderField.declaredClass.methods.single { it.name == "show" && it.paramCount == 0 }
    fun isGet(method: MethodData) = method.className == media.name &&
        method.returnTypeName == full && method.paramTypeNames.firstOrNull() == input && method.paramCount >= 4
    val init = loader.methods.singleOrNull { method ->
        method.paramCount == 0 && method.returnTypeName == "void" && method.invokes.any(::isGet)
    }
    // R8 可把 loader.start/init 内联到弹窗 show，保留实际发起加载的位置。
    val get = (show.invokes + init?.invokes.orEmpty()).filter(::isGet).distinctBy { it.descriptor }.single()
    val listener = loader.methods.single {
        it.paramTypeNames == listOf("int", "int", "java.lang.Object[]")
    }
    val event = listener.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
        it.className == notification && it.typeName == "int" && Modifier.isStatic(it.modifiers)
    }
    val fetch = media.methods.single {
        it.returnTypeName == "void" && it.paramTypeNames.firstOrNull() == input &&
            it.paramCount == 2 && it.paramTypes.last().let { type ->
                Modifier.isInterface(type.modifiers) && type.methods.any { method -> method.paramCount == 2 }
            }
    }
    val put = media.methods.single { it.paramTypeNames == listOf(full, "boolean") }
    val post = put.invokes.distinctBy { it.descriptor }.single {
        it.className == notification && it.paramTypeNames == listOf("int", "java.lang.Object[]")
    }
    val getNotification = generateSequence(media) { it.superClass }.flatMap { it.methods }.single {
        it.paramCount == 0 && it.returnTypeName == notification
    }
    return mapOf(
        "load" to load.descriptor,
        "getName" to load.invokes.single { it.returnTypeName == full && it.paramTypeNames == listOf("java.lang.String") }.descriptor,
        "getId" to load.invokes.single { it.returnTypeName == full && it.paramTypeNames == listOf("long") }.descriptor,
        "input" to alert.fields.single { it.typeName == input }.descriptor,
        "set" to alert.fields.single { it.typeName == full }.descriptor,
        "emojiShow" to show.descriptor,
        "emojiInit" to init?.descriptor.orEmpty(),
        "emojiLoader" to loaderField.descriptor,
        "emojiNotification" to listener.descriptor,
        "get" to get.descriptor,
        "fetch" to fetch.descriptor,
        "put" to put.descriptor,
        "post" to post.descriptor,
        "notification" to getNotification.descriptor,
        "event" to event.descriptor,
    )
}
