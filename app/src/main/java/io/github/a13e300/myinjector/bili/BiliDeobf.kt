package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateBiliShare(bridge: DexKitBridge): ObfsInfo = bridge.findMethod {
    matcher {
        usingEqStrings("share.helper.inner", "mCallback.get() is null")
        paramTypes("java.lang.String", "android.os.Bundle")
        returnType("void")
    }
}.single().toObfsInfo()

internal fun locateBiliUnderPlayerAds(bridge: DexKitBridge): List<ObfsInfo> {
    // 新版把创建逻辑内联进 Flow 的 SuspendLambda，且 Theseus / Theseus2 同时存在。
    val methods = (bridge.findMethod {
        matcher { usingEqStrings("DetailAdService-onCreateViews") }
    } + bridge.findMethod {
        matcher { usingEqStrings("DetailAdService", "onCreateViews") }
    }).distinctBy { it.descriptor }
    check(methods.isNotEmpty()) { "No under-player ad renderer found" }
    methods.forEach {
        check(it.returnTypeName == "void" ||
            (it.methodName == "invokeSuspend" && it.paramTypeNames == listOf("java.lang.Object") &&
                it.returnTypeName == "java.lang.Object")) { "Unexpected ad renderer: ${it.descriptor}" }
    }
    return methods.map { it.toObfsInfo() }
}

internal fun locateBiliPausePageConfig(bridge: DexKitBridge): ObfsInfo = bridge.findMethod {
    matcher {
        usingEqStrings("dd_ad_pausedpage_enable")
        paramTypes()
        returnType("boolean")
    }
}.single().toObfsInfo()

internal fun locateBiliSplash(bridge: DexKitBridge): ObfsInfo = bridge.findMethod {
    matcher { usingEqStrings("show splash id = ", "ADSplashFragment") }
}.single().toObfsInfo()

internal fun locateBiliNewSplash(bridge: DexKitBridge): ObfsInfo = bridge.findMethod {
    matcher {
        usingEqStrings("[KSplash]SplashOrderSelectComponent", "getValidResSplash, splashId = ")
        paramCount(5)
    }
}.single().toObfsInfo()

internal fun locateBiliActivityMetaField(bridge: DexKitBridge): ObfsInfo {
    // Kotlin 序列化直接读取字段，Hook getter 无法修改发给 Chronos 的 protobuf 字节。
    val getter = bridge.getMethodData(
        "Lcom/bapis/bilibili/community/service/dm/v1/KDmViewReply;->getActivityMeta()Ljava/util/List;"
    ) ?: error("KDmViewReply.getActivityMeta not found")
    return getter.usingFields.single {
        it.usingType == FieldUsingType.Read && it.field.className == getter.className &&
            it.field.typeName == "java.util.List"
    }.field.toObfsInfo()
}
