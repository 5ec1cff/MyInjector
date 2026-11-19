package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.result.MethodData

private fun MethodData.readFields() = usingFields.filter {
    it.usingType == FieldUsingType.Read && it.field.className == className
}.map { it.field }.distinctBy { it.descriptor }

private fun locateHomeVideoMenu(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val phone = bridge.findMethod { matcher {
        usingEqStrings("showFeedbackDialogV5, anchor fragment not attached")
    } }.single()
    val wide = phone.callers.single { it.className == phone.className }
    val callbacks = (phone.callers + wide.callers).filter { it.methodName == "onDataSuccess" }
        .distinctBy { it.descriptor }.sortedBy { it.descriptor }
    check(callbacks.size == 4)
    val holderItem = callbacks.flatMap { it.invokes }.distinctBy { it.descriptor }.single {
        it.paramCount == 0 && it.returnTypeName == "com.bilibili.bilifeed.card.FeedItem"
    }
    val dragMenu = bridge.getClassData(
        "com.bilibili.app.comm.list.common.widget.bottomsheet.drag.ListThreePointMenuDialogFragment"
    )!!.methods.single { it.methodName == "show\$default" }
    fun videoHolder(name: String): Boolean {
        var type = bridge.getClassData(name)
        while (type != null) {
            if (type.name == holderItem.className || type.name == "com.bilibili.pegasus.BasePegasusHolder") return true
            type = type.superClass
        }
        return false
    }
    val entries = dragMenu.callers.filter { method ->
        method.paramTypeNames.any(::videoHolder)
    }.distinctBy { it.descriptor }.sortedBy { it.descriptor }
    check(entries.size == 2)
    return callbacks.mapIndexed { index, method -> "callback$index" to method.toObfsInfo() }.toMap() +
        entries.mapIndexed { index, method -> "entry$index" to method.toObfsInfo() }.toMap() +
        mapOf("holderItem" to holderItem.toObfsInfo(), "dragMenu" to dragMenu.toObfsInfo())
}

private fun locateRelateVideoMenu(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val show = bridge.findMethod { matcher {
        usingEqStrings("VIEW_ORIGIN", "VIEW_TRANSLATED")
        paramCount = 13
    } }.single { it.paramTypeNames.first() == "android.content.Context" }
    val cardName = show.paramTypeNames[3]
    val basic = bridge.findMethod { matcher { usingEqStrings("RelateCardBasicInfo(title=") } }
        .single { it.className.startsWith("com.bilibili.").not() }
    val card = bridge.findMethod { matcher {
        declaredClass(cardName); name = "toString"
    } }.single()
    val fields = card.readFields()
    val add = show.invokes.single {
        it.className == show.paramTypeNames[6] &&
            it.paramTypeNames == listOf("java.lang.String", "kotlin.jvm.functions.Function0")
    }
    val nf = show.invokes.single { it.paramCount == 8 && cardName in it.paramTypeNames }
    val callbackClass = nf.invokes.single {
        it.methodName == "<init>" && cardName in it.paramTypeNames
    }.declaredClass!!
    return mapOf(
        "show" to show.toObfsInfo(), "add" to add.toObfsInfo(),
        "basic" to fields.single { it.typeName == basic.className }.toObfsInfo(),
        "nf" to fields.single { it.typeName == nf.paramTypeNames[3] }.toObfsInfo(),
        "threePoint" to fields[3].toObfsInfo(),
        "goto" to fields.first { it.typeName == "java.lang.String" }.toObfsInfo(),
        "id" to basic.readFields().last { it.typeName == "long" }.toObfsInfo(),
        "feedback" to callbackClass.methods.single { it.methodName == "onDataSuccess" }.toObfsInfo(),
    )
}

private fun locateSearchVideoMenu(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val show = bridge.findMethod { matcher { usingEqStrings("kntr_ugc_feedback") } }
        .single { "com.bilibili.search2.api.BaseSearchItem" in it.paramTypeNames }
    val panel = bridge.findMethod { matcher {
        usingEqStrings("kntr.app.search.feedback.ui.UGCFeedbackPanel (UGCFeedbackPanel.kt:73)")
    } }.single()
    val grid = bridge.findMethod { matcher {
        usingEqStrings("kntr.app.search.feedback.ui.ReasonGrid (UGCFeedbackPanel.kt:287)")
    } }.single()
    val section = bridge.findMethod { matcher { usingEqStrings("UGCFeedbackSection(title=") } }.single()
    val reason = bridge.findMethod { matcher { usingEqStrings("UGCFeedbackReason(id=") } }.single()
    return mapOf("show" to show.toObfsInfo(), "panel" to panel.toObfsInfo(),
        "grid" to grid.toObfsInfo(), "section" to section.declaredClass!!.toObfsInfo(),
        "reason" to reason.declaredClass!!.toObfsInfo())
}

private fun locateNewRelateVideoMenu(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val prefix = "com.bilibili.ship.theseus2.united.page.intro.module.relate."
    val helper = bridge.getClassData(prefix + "RelateCardMoreMenuHelper")!!
    val card = bridge.getClassData(prefix + "RelateCard")!!
    val show = helper.methods.single { it.paramCount == 13 && it.paramTypeNames.first() == helper.name }
    val add = show.invokes.single {
        it.paramTypeNames.takeLast(2) == listOf("java.lang.String", "kotlin.jvm.functions.Function0") &&
            it.paramCount == 3 && it.methodName != "<init>"
    }
    val service = bridge.getClassData(add.paramTypeNames[0])!!
    val basic = bridge.findMethod { matcher { usingEqStrings("RelateCardBasicInfo(title=") } }
        .single { it.className.startsWith(prefix) }
    val nf = bridge.findMethod { matcher {
        usingEqStrings("https://app.bilibili.com/x/v2/feed/nf")
    } }.single { it.className.startsWith(prefix) }
    val nfMenu = nf.invokes.single {
        it.methodName == "<init>" && it.paramCount == 5 && it.className.startsWith(prefix)
    }
    val callback = nf.paramTypeNames[4]
    val response = bridge.findMethod { matcher { usingEqStrings("RelateNfResponse(groups=") } }.single()
    val group = bridge.findMethod { matcher { usingEqStrings("RelateNfGroup(style=") } }.single()
    val item = bridge.findMethod { matcher { usingEqStrings("RelateNfItem(id=") } }.single()
    return mapOf(
        "show" to show.toObfsInfo(), "add" to add.toObfsInfo(),
        "service" to helper.fields.single { it.typeName == service.name }.toObfsInfo(),
        "display" to service.methods.single { it.methodName != "<init>" }.toObfsInfo(),
        "basic" to card.fields.single { it.typeName == basic.className }.toObfsInfo(),
        "goto" to card.methods.single { "av" in it.usingStrings && "app-av" in it.usingStrings }.toObfsInfo(),
        "id" to basic.readFields().last { it.typeName == "long" }.toObfsInfo(),
        "nfMenu" to nfMenu.toObfsInfo(),
        "nfCard" to bridge.getClassData(callback)!!.fields.single { it.typeName == card.name }.toObfsInfo(),
        "nfClick" to bridge.getClassData(callback)!!.methods.single { it.methodName == "invoke" }.toObfsInfo(),
        "response" to response.declaredClass!!.toObfsInfo(),
        "group" to group.declaredClass!!.toObfsInfo(), "item" to item.declaredClass!!.toObfsInfo(),
    )
}

internal fun biliVideoCardTaskMembers(creator: ObfsTableCreator, kind: String): Map<String, ObfsInfo> {
    val found by lazy {
        when (kind) {
            "home" -> locateHomeVideoMenu(creator.bridge)
            "relate" -> locateRelateVideoMenu(creator.bridge)
            "search" -> locateSearchVideoMenu(creator.bridge)
            "newRelate" -> locateNewRelateVideoMenu(creator.bridge)
            else -> error("Unknown video menu: $kind")
        }
    }
    val keys = when (kind) {
        "home" -> listOf("callback0", "callback1", "callback2", "callback3", "holderItem",
            "entry0", "entry1", "dragMenu")
        "relate" -> listOf("show", "add", "basic", "goto", "id", "nf", "threePoint", "feedback")
        "search" -> listOf("show", "panel", "grid", "section", "reason")
        "newRelate" -> listOf("show", "add", "service", "display", "basic", "goto", "id",
            "nfMenu", "nfCard", "nfClick", "response", "group", "item")
        else -> error("Unknown video menu: $kind")
    }
    return keys.associateWith { key -> creator.create("videoCardTask.$kind.$key") { found.getValue(key) } }
}
