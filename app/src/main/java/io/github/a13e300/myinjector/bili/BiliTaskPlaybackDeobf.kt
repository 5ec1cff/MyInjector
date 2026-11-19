package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateBiliTaskPlayback(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val top = bridge.findMethod { matcher {
        usingEqStrings("skip pause: watch-and-play session active")
        paramTypes("java.lang.Object"); returnType("java.lang.Object")
    } }.single()
    val visible = bridge.findMethod { matcher {
        usingEqStrings("Keep source player available for watch-and-play page transition", ", new page")
        paramTypes("int", "int"); returnType("void")
    } }.single()
    val keep = top.invokes.distinctBy { it.descriptor }.single { method ->
        method.returnTypeName == "boolean" && method.invokes.any {
            it.className == "com.bilibili.gripper.api.flash.game.watchandplay.GGameWatchAndPlayModeService" &&
                it.methodName == "isInWatchAndPlayMode"
        }
    }
    check(keep.paramTypeNames == listOf(keep.className))
    check(visible.invokes.any { it.descriptor == keep.descriptor })
    val owners = listOf("topOwner" to top, "visibleOwner" to visible).associate { (key, method) ->
        key to method.usingFields.filter {
            it.usingType == FieldUsingType.Read && it.field.className == method.className &&
                it.field.typeName == keep.className
        }.map { it.field }.distinctBy { it.descriptor }.single().toObfsInfo()
    }
    val container = keep.declaredClass!!.fields.single {
        it.typeName == "tv.danmaku.biliplayerv2.PlayerContainer"
    }
    return owners + mapOf("top" to top.toObfsInfo(), "visible" to visible.toObfsInfo(),
        "keep" to keep.toObfsInfo(), "container" to container.toObfsInfo())
}

internal fun biliTaskPlaybackMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliTaskPlayback(creator.bridge) }
    return listOf("top", "visible", "topOwner", "visibleOwner", "keep", "container").associateWith { key ->
        creator.create("taskPlayback.$key") { found.getValue(key) }
    }
}
