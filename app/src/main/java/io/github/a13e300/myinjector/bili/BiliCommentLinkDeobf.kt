package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateBiliCommentLink(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val menu = bridge.findMethod { matcher {
        usingEqStrings("kntr.common.comment.page.ui.CommentMoreMenu (CommentMoreMenu.kt:38)")
    } }.single()
    val overlay = bridge.findMethod { matcher {
        usingEqStrings("MoreMenuOverlay(cardId=", ", commentId=", ", rootCommentId=")
    } }.single()
    check(menu.paramTypeNames.first() == overlay.className)
    val fields = overlay.usingFields.filter {
        it.usingType == FieldUsingType.Read && it.field.className == overlay.className
    }.map { it.field }.distinctBy { it.descriptor }
    val ids = fields.filter { it.typeName == "long" }
    val entry = bridge.findMethod { matcher { usingEqStrings("MoreMenuEntry(title=") } }.single()
    val entryFields = entry.usingFields.filter {
        it.usingType == FieldUsingType.Read && it.field.className == entry.className
    }.map { it.field }.distinctBy { it.descriptor }
    val result = mutableMapOf(
        "menu" to menu.toObfsInfo(),
        "commentId" to ids[1].toObfsInfo(),
        "rootCommentId" to ids[2].toObfsInfo(),
        "oid" to fields.single { it.typeName == "java.lang.Long" }.toObfsInfo(),
        "type" to fields.single { it.typeName == "java.lang.Integer" }.toObfsInfo(),
        "entryTitle" to entryFields.first().toObfsInfo(),
        "entryIcon" to entryFields[1].toObfsInfo(),
    )
    for ((key, trace) in mapOf(
        "panel" to "kntr.common.comment.common.ui.moremenu.MoreMenuPanel (MoreMenuPanel.kt:43)",
        "sheet" to "kntr.common.comment.common.ui.moremenu.MoreMenuSheet (MoreMenuSheet.kt:50)",
        "bubble" to "kntr.common.comment.common.ui.moremenu.MoreMenuBubble (MoreMenuBubble.kt:204)",
    )) {
        val renderer = bridge.findMethod { matcher { usingEqStrings(trace) } }.single()
        check(renderer.paramTypeNames.count { it == "java.util.List" } == 1)
        check(menu.invokes.any { it.descriptor == renderer.descriptor })
        result[key] = renderer.toObfsInfo()
    }
    return result
}

internal fun biliCommentLinkMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliCommentLink(creator.bridge) }
    return listOf("menu", "commentId", "rootCommentId", "oid", "type", "entryTitle", "entryIcon",
        "panel", "sheet", "bubble").associateWith { key ->
        creator.create("commentLink.$key") { found.getValue(key) }
    }
}
