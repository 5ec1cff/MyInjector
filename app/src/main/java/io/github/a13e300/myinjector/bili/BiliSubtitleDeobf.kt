package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateBiliSubtitles(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val callbackName = "com.bilibili.ship.theseus2.ugc.intro.kingposition.UgcKingPositionService\$createSharePanel\$callback\$1"
    val ownerName = "com.bilibili.ship.theseus2.ugc.intro.kingposition.UgcKingPositionService"
    val serviceName = "com.bilibili.ship.theseus2.united.page.subtitle.TheseusSubtitleService"
    fun field(owner: String, fieldType: String) = bridge.findField { matcher {
        declaredClass(owner); type(fieldType)
    } }.single()
    val menu = bridge.findMethod { matcher {
        declaredClass(callbackName); paramTypes("java.util.List"); returnType("java.util.List")
    } }.single()
    val item = bridge.findMethod { matcher {
        usingEqStrings("Item(targetName=", ", localIconResource=", ", selectedStatusIndex=0, textColor=")
    } }.single()
    val target = item.usingFields.first {
        it.usingType == FieldUsingType.Read && it.field.className == item.className &&
            it.field.typeName == "java.lang.String"
    }.field
    val click = menu.declaredClass!!.methods.single {
        it.paramTypeNames == listOf(item.className) && it.returnTypeName == "boolean"
    }
    // 原回调在 onPanelDismissed 中处理 PIC，沿用这个时机打开字幕窗口。
    val dismiss = menu.declaredClass!!.methods.single {
        it.paramCount == 0 && it.returnTypeName == "void" &&
            it.invokes.any { call -> call.className == "kotlinx.coroutines.BuildersKt" }
    }
    val state = bridge.findMethod { matcher { usingEqStrings("SubtitleState(loading=", ", available=") } }.single()
    val stateFields = state.usingFields.filter {
        it.usingType == FieldUsingType.Read && it.field.className == state.className
    }.map { it.field }.distinctBy { it.descriptor }
    val getter = bridge.findMethod { matcher {
        declaredClass(serviceName); paramTypes(); returnType(state.className)
    } }.single()
    val reply = bridge.findMethod { matcher { usingEqStrings("KSubtitleViewReply(subtitle=") } }.single()
    val mediaGetter = bridge.findMethod { matcher {
        returnType("com.bilibili.ship.theseus2.united.page.playingarea.PlayingAreaDescription")
        paramTypes()
    } }.filter { method ->
        bridge.findField { matcher { declaredClass(serviceName); type(method.className) } }.isNotEmpty()
    }.single()
    val content = bridge.findMethod { matcher {
        usingEqStrings("kntr.common.share.common.ui.ShareMenuItemContent (ShareMenuItem.kt:76)")
    } }.single()
    val clickable = content.invokes.single {
        it.className == "androidx.compose.foundation.ClickableKt" && it.paramCount == 9 &&
            it.paramTypeNames[3] == "boolean"
    }
    val filter = bridge.findMethod { matcher {
        usingEqStrings("<ShareChannelTransformer> 开始转换渠道列表")
        paramCount(2)
    } }.single()
    val toolbar = "com.bilibili.ship.theseus.united.page.toolbar.MenuService"
    val toolbarGroupClass = "$toolbar\$createSettingGroup\$2"
    val toolbarGroup = bridge.findMethod { matcher {
        declaredClass(toolbarGroupClass); name("invokeSuspend"); paramTypes("java.lang.Object")
    } }.single()
    val toolbarItem = bridge.findMethod { matcher {
        usingEqStrings("com.bilibili.playerbizcommonv2.widget.setting.channel.VideoSettingButtonComponent.SettingItem (VideoSettingButtonComponent.kt:61)")
    } }.single()
    val toolbarClickable = toolbarItem.invokes.single {
        it.className == "com.bilibili.compose.utils.ModifierUtilsKt" && it.methodName == "clickableNoInteraction\$default"
    }
    return mapOf(
        "menu" to menu.toObfsInfo(), "click" to click.toObfsInfo(), "dismiss" to dismiss.toObfsInfo(),
        "callbackOwner" to field(callbackName, ownerName).toObfsInfo(),
        "ownerMedia" to field(ownerName, mediaGetter.className).toObfsInfo(),
        "serviceMedia" to field(serviceName, mediaGetter.className).toObfsInfo(),
        "ownerContext" to field(ownerName, "kntr.common.trio.vertex.VertexContext").toObfsInfo(),
        "state" to getter.toObfsInfo(),
        "loading" to stateFields.first { it.typeName == "boolean" }.toObfsInfo(),
        "error" to stateFields.single { it.typeName == "java.lang.Throwable" }.toObfsInfo(),
        "reply" to field(serviceName, reply.className).toObfsInfo(),
        "target" to target.toObfsInfo(), "content" to content.toObfsInfo(), "clickable" to clickable.toObfsInfo(),
        "filter" to filter.toObfsInfo(),
        "channelItems" to field(filter.returnTypeName, "java.util.List").toObfsInfo(),
        "toolbarContext" to field(toolbar, "androidx.fragment.app.FragmentActivity").toObfsInfo(),
        "toolbarContainer" to field(toolbar, "tv.danmaku.biliplayerv2.PlayerContainer").toObfsInfo(),
        "toolbarGroup" to toolbarGroup.toObfsInfo(),
        "toolbarGroupOwner" to field(toolbarGroupClass, toolbar).toObfsInfo(),
        "toolbarItem" to toolbarItem.toObfsInfo(), "toolbarClickable" to toolbarClickable.toObfsInfo(),
        "legacyContainer" to field("tv.danmaku.biliplayerv2.service.interact.biz.InteractLayerService",
            "tv.danmaku.biliplayerv2.PlayerContainer").toObfsInfo(),
    )
}

internal fun biliSubtitleMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliSubtitles(creator.bridge) }
    return listOf("menu", "click", "dismiss", "callbackOwner", "ownerMedia", "serviceMedia", "ownerContext",
        "state", "loading", "error", "reply", "target", "content", "clickable", "filter", "channelItems",
        "toolbarContext", "toolbarContainer", "toolbarGroup", "toolbarGroupOwner", "toolbarItem", "toolbarClickable",
        "legacyContainer").associateWith { key ->
        creator.create("subtitle.$key") { found.getValue(key) }
    }
}
