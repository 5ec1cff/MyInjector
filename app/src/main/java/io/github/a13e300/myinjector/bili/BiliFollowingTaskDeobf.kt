package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateBiliFollowingTask(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val actionName = bridge.findMethod { matcher {
        usingEqStrings("interaction_decoration", "interaction_autoplay_set", "interaction_watch_later")
    } }.single()
    val service = actionName.declaredClass!!
    val show = service.methods.single {
        it.paramCount == 4 && it.paramTypeNames.first() == "android.content.Context" &&
            it.paramTypeNames.last() == "android.graphics.Rect" && it.returnTypeName == "void"
    }
    val click = service.methods.single {
        it.paramCount == 5 && it.paramTypeNames[2] == actionName.paramTypeNames[0] && it.returnTypeName == "void"
    }
    val adapter = show.invokes.filter {
        it.methodName == "<init>" && it.paramTypeNames == listOf("android.content.Context", "java.util.List",
            "kotlin.jvm.functions.Function2", "kotlin.jvm.functions.Function2")
    }.distinctBy { it.descriptor }.single()
    val card = show.usingFields.filter {
        it.usingType == FieldUsingType.Read && it.field.className == show.paramTypeNames[1]
    }.map { it.field }.distinctBy { it.descriptor }.single()
    val cardClass = bridge.getClassData(card.typeName)!!
    val video = bridge.findMethod { matcher {
        usingEqStrings(" ---> \nModuleVideo, ", ", aid ", ", isPGC ")
    } }.single()
    val aid = video.usingFields.filter {
        it.usingType == FieldUsingType.Read && it.field.className == video.className && it.field.typeName == "long"
    }.map { it.field }.distinctBy { it.descriptor }.first()
    return mapOf(
        "show" to show.toObfsInfo(), "click" to click.toObfsInfo(), "adapter" to adapter.toObfsInfo(),
        "card" to card.toObfsInfo(),
        "type" to cardClass.fields.single { it.typeName == "int" }.toObfsInfo(),
        "video" to cardClass.fields.single { it.typeName == video.className }.toObfsInfo(),
        "aid" to aid.toObfsInfo(),
        "dynamicId" to cardClass.methods.single {
            it.paramCount == 0 && it.returnTypeName == "long"
        }.toObfsInfo(),
    )
}

internal fun biliFollowingTaskMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliFollowingTask(creator.bridge) }
    return listOf("show", "click", "adapter", "card", "type", "video", "aid", "dynamicId").associateWith { key ->
        creator.create("followingTask.$key") { found.getValue(key) }
    }
}
