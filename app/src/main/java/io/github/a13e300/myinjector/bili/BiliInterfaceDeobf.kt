package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldUsingType

internal fun locateBiliVideoVersion(bridge: DexKitBridge): ObfsInfo = bridge.findMethod { matcher {
    usingEqStrings("kmp_theseus")
    paramTypes(); returnType("boolean")
} }.single().toObfsInfo()

internal fun locateBiliMineVersion(bridge: DexKitBridge): ObfsInfo = bridge.findMethod { matcher {
    usingEqStrings("bilibili://user_center/mine/v2")
    name("intercept")
} }.single().usingFields.single {
    it.usingType == FieldUsingType.Read && it.field.typeName == "kotlin.Lazy"
}.field.toObfsInfo()

internal fun locateBiliSplashProducer(bridge: DexKitBridge): ObfsInfo = bridge.findMethod { matcher {
    usingEqStrings("dd_splash_business_use_cmp_exp")
    paramTypes("android.app.Application", "com.bilibili.lib.dd.GDeviceDecision")
} }.single().toObfsInfo()

internal fun locateBiliInterfaceTrace(bridge: DexKitBridge, trace: String): ObfsInfo =
    bridge.findMethod { matcher { usingEqStrings(trace) } }.single().toObfsInfo()

internal fun locateBiliNewVideoCreated(bridge: DexKitBridge): ObfsInfo {
    val enter = bridge.findMethod { matcher {
        usingEqStrings("Theseus detail vertex started route=")
    } }.single()
    return enter.invokes.single {
        it.className == "com.bilibili.ship.theseus2.detail.BusinessScopeDriverImpl" &&
            it.paramCount == 0 && it.returnTypeName == "void"
    }.toObfsInfo()
}
