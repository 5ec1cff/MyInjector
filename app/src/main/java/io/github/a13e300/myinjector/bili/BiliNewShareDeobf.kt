package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateBiliNewShare(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val engine = bridge.findMethod { matcher {
        declaredClass("kntr.common.share.core.ShareEngine")
        usingEqStrings("shareToTarget: disableFetch=true, 取消网络请求")
        paramCount(5)
        returnType("java.lang.Object")
    } }.single()
    check(engine.paramTypeNames[2] == "boolean")
    val config = bridge.findMethod { matcher {
        usingEqStrings("ShareConfig(shareContentProvider=", ", disableFetchShareContent=")
        paramTypes(); returnType("java.lang.String")
    } }.single().declaredClass!!
    val content = config.findMethod { matcher {
        paramTypes(engine.paramTypeNames[0]); returnType(engine.paramTypeNames[1])
    } }.single()
    val factory = content.invokes.single {
        it.paramTypeNames == listOf(engine.paramTypeNames[0]) &&
            it.returnTypeName == "kotlin.jvm.functions.Function0"
    }
    val providerField = content.usingFields.single {
        it.usingType == FieldUsingType.Read && it.field.className == config.name &&
            it.field.typeName == factory.className
    }.field
    val baseInfo = bridge.findMethod { matcher {
        usingEqStrings("ShareBaseInfo(title=", ", content=", ", link=")
        paramTypes(); returnType("java.lang.String")
    } }.single()
    // toString 依次读取 title、content、link。
    val baseFields = baseInfo.usingFields.filter {
        it.usingType == FieldUsingType.Read && it.field.className == baseInfo.className &&
            it.field.typeName == "java.lang.String"
    }.map { it.field }.distinctBy { it.descriptor }
    check(baseFields.size == 3)
    val copy = bridge.findMethod { matcher {
        usingEqStrings("CopyShareContent(content=")
        paramTypes(); returnType("java.lang.String")
    } }.single()
    val copyText = copy.usingFields.single {
        it.usingType == FieldUsingType.Read && it.field.className == copy.className &&
            it.field.typeName == "java.lang.String"
    }.field
    check(copy.declaredClass!!.superClass!!.name == engine.paramTypeNames[1])
    return mapOf(
        "engine" to engine.toObfsInfo(),
        "contentProvider" to content.toObfsInfo(),
        "providerField" to providerField.toObfsInfo(),
        "findFactory" to factory.toObfsInfo(),
        "baseInfoTitle" to baseFields[0].toObfsInfo(),
        "baseInfoContent" to baseFields[1].toObfsInfo(),
        "baseInfoLink" to baseFields.last().toObfsInfo(),
        "copyText" to copyText.toObfsInfo(),
    )
}

internal fun biliNewShareMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliNewShare(creator.bridge) }
    return listOf("engine", "contentProvider", "providerField", "findFactory",
        "baseInfoTitle", "baseInfoContent", "baseInfoLink", "copyText")
        .associateWith { key -> creator.create("newShare.$key") { found.getValue(key) } }
}
