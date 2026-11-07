package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

internal fun findStickerStream(bridge: DexKitBridge): Map<String, String> {
    val stream = bridge.getClassData("org.telegram.messenger.AnimatedFileDrawableStream")!!
    val operation = stream.fields.singleOrNull { it.name == "loadOperation" }
        ?: stream.fields.single { field -> field.type.fields.any { it.typeName == "int" && Modifier.isVolatile(it.modifiers) } }
    val state = operation.type.fields.singleOrNull { it.name == "state" }
        ?: operation.type.fields.single { it.typeName == "int" && Modifier.isVolatile(it.modifiers) }
    return mapOf("operation" to operation.descriptor, "state" to state.descriptor,
        "latch" to stream.fields.single { it.typeName == "java.util.concurrent.CountDownLatch" }.descriptor)
}
