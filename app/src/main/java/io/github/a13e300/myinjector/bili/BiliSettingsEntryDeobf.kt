package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import java.lang.reflect.Modifier

internal const val BILI_MINE_ITEM = "com.bilibili.lib.homepage.mine.MenuGroup\$Item"
internal const val BILI_MINE_V2_ITEM = "com.bilibili.lib.homepage.mine.MenuGroupV2\$Item"

internal fun locateBiliLegacySettingsEntry(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val render = bridge.findMethod { matcher {
        usingEqStrings("activity://main/preference")
        declaredClass("tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment")
        paramTypes("tv.danmaku.bili.ui.main2.api.AccountMine")
        returnType("void")
    } }.single()
    val groups = render.declaredClass!!.fields.single { it.typeName == "java.util.List" }
    val click = bridge.findMethod { matcher {
        paramTypes(BILI_MINE_ITEM)
        returnType("void")
    } }.single { method -> method.invokes.any {
        it.isConstructor && it.className == "tv.danmaku.bili.ui.main2.mine.MinePageManager\$switchTo\$1"
    } }
    val fragment = click.declaredClass!!.fields.single { it.typeName == render.className }
    return mapOf("render" to render.toObfsInfo(), "groups" to groups.toObfsInfo(),
        "click" to click.toObfsInfo(), "fragment" to fragment.toObfsInfo())
}

internal fun locateBiliNewSettingsClick(bridge: DexKitBridge): ObfsInfo =
    bridge.findMethod { matcher {
        paramTypes("tv.danmaku.bili.MainActivityV2", BILI_MINE_V2_ITEM)
        returnType("void")
    } }.single { method ->
        !Modifier.isFinal(method.declaredClass!!.modifiers) && method.invokes.any {
            it.className == "com.bilibili.lib.blrouter.RouteRequestKt" && it.methodName == "toRouteRequest"
        }
    }.toObfsInfo()

internal fun biliLegacySettingsEntryMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliLegacySettingsEntry(creator.bridge) }
    return listOf("render", "groups", "click", "fragment").associateWith { key ->
        creator.create("settingsEntry.legacy.$key") { found.getValue(key) }
    }
}
