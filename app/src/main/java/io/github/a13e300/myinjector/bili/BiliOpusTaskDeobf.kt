package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge

internal fun locateBiliOpusTask(bridge: DexKitBridge): Map<String, ObfsInfo> {
    fun traced(name: String, file: String) = bridge.findMethod { matcher {
        usingEqStrings("kntr.app.following.opusDetail.ui.chrome.$name ($file)")
    } }.single()
    return mapOf(
        "bar" to traced("OpusDetailNavBar.<anonymous>.<anonymous>", "OpusDetailNavBar.kt:160").toObfsInfo(),
        "pillBar" to traced("OpusMaskedActionPill", "OpusMaskedActionPill.kt:78").toObfsInfo(),
        "icon" to traced("NavIconButton", "OpusDetailNavBar.kt:292").toObfsInfo(),
        "pill" to traced("PillIconButton", "OpusMaskedActionPill.kt:154").toObfsInfo(),
    )
}

internal fun biliOpusTaskMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliOpusTask(creator.bridge) }
    return listOf("bar", "pillBar", "icon", "pill").associateWith { key ->
        creator.create("opusTask.$key") { found.getValue(key) }
    }
}
