package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge

internal fun findSystemEmoji(bridge: DexKitBridge): Map<String, String> {
    val emoji = bridge.getClassData(telegramApiClass(bridge, "org.telegram.messenger.Emoji"))!!
    val simple = bridge.getClassData(telegramApiClass(bridge, "org.telegram.messenger.Emoji\$SimpleEmojiDrawable"))!!
    val info = simple.fields.single { it.typeName !in listOf("boolean", "android.graphics.Rect", "android.graphics.Paint") }
    val data = telegramApiClass(bridge, "org.telegram.messenger.EmojiData")
    // Emoji builds the page/index table from data, not dataColored.
    val table = emoji.methods.single { it.name == "<clinit>" }.usingFields.map { it.field }.distinctBy { it.descriptor }
        .single { it.className == data && it.typeName == "java.lang.String[][]" }
    val fullSize = simple.superClass!!.fields.single { it.typeName == "boolean" }
    val size = emoji.fields.singleOrNull { it.name == "bigImgSize" } ?: run {
        val bigMethod = emoji.methods.single { m -> m.usingFields.any {
            it.field.descriptor == fullSize.descriptor && it.usingType == org.luckypray.dexkit.result.FieldUsingType.Write
        } }
        scanTelegramDex(bridge, bigMethod).calls.last { it.method.name == "setBounds" }.args.last()!!.field!!
    }
    return mapOf(
        "fix" to (emoji.methods.singleOrNull { it.name == "fixEmoji" } ?: emoji.methods.single { it.paramTypeNames == listOf("java.lang.String") && it.returnTypeName == "java.lang.String" }).descriptor,
        "size" to size.descriptor,
        "fullSize" to fullSize.descriptor,
        "info" to info.descriptor,
        "page" to info.type.fields.single { it.typeName == "byte" }.descriptor,
        "index" to (info.type.fields.singleOrNull { it.name == "emojiIndex" }
            ?: info.type.fields.single { it.typeName == "short" }).descriptor,
        "data" to table.descriptor,
        "draw" to simple.methods.single { it.name == "draw" && it.paramTypeNames == listOf("android.graphics.Canvas") }.descriptor,
    )
}
