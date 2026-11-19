package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateBiliAvHeadline(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val render = bridge.findMethod { matcher {
        usingEqStrings("AV", "bv.pattern_rule_bv_only")
        paramTypes("androidx.compose.ui.Modifier", "androidx.compose.runtime.Composer", "int")
        returnType("void")
    } }.single()
    val calls = render.invokes.distinctBy { it.descriptor }
    val aid = calls.single {
        it.className.startsWith("com.bilibili.ship.theseus2.ugc.intro.ugcheadline.") &&
            it.paramCount == 0 && it.returnTypeName == "long"
    }
    val bvid = calls.single {
        it.className == aid.className && it.paramCount == 0 && it.returnTypeName == "java.lang.String"
    }
    val helper = render.usingFields.filter {
        it.usingType == FieldUsingType.Read && it.field.className == render.className &&
            it.field.typeName == aid.className
    }.map { it.field }.distinctBy { it.descriptor }.single()
    val item = bridge.findMethod { matcher { usingEqStrings("UgcMetaItem(text=") } }.single()
        .declaredClass!!.methods.single { it.isConstructor }
    check(render.invokes.any { it.descriptor == item.descriptor })
    return mapOf("render" to render.toObfsInfo(), "helper" to helper.toObfsInfo(),
        "aid" to aid.toObfsInfo(), "bvid" to bvid.toObfsInfo(), "item" to item.toObfsInfo())
}

internal fun biliAvHeadlineMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliAvHeadline(creator.bridge) }
    return listOf("render", "helper", "aid", "bvid", "item").associateWith { key ->
        creator.create("showAv.$key") { found.getValue(key) }
    }
}
