package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge

internal fun locateBiliCommentRoutes(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val legacy = bridge.findMethod { matcher {
        usingEqStrings("reply", "comment", "detail", "anchor", "showEnter", "syncFollowing")
        paramTypes("com.bilibili.lib.blrouter.RouteInterceptor\$Chain")
        returnType("com.bilibili.lib.blrouter.RouteResponse")
    } }.single()
    val theseus2 = bridge.findMethod { matcher {
        usingEqStrings("UnitedDetailInterceptor", "reply", "comment", "detail", "anchor", "showEnter", "syncFollowing")
        paramCount(3)
        returnType("kntr.base.router.target.RouterTarget")
    } }.single()
    check(theseus2.paramTypeNames.first() == "kntr.base.router.target.RouterTargetContext")
    return mapOf("legacy" to legacy.toObfsInfo(), "theseus2" to theseus2.toObfsInfo())
}

internal fun biliCommentRouteMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliCommentRoutes(creator.bridge) }
    return listOf("legacy", "theseus2").associateWith { key ->
        creator.create("commentRoute.$key") { found.getValue(key) }
    }
}
