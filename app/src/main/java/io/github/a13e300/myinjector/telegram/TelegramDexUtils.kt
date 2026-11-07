package io.github.a13e300.myinjector.telegram

import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

// Follow straight-line register origins around resource assignments to distinguish
// multiple view fields of the same type, including views kept in constructor locals.
internal fun viewFieldWithResource(bridge: DexKitBridge, method: MethodData, resource: String): FieldData {
    return scanTelegramDex(bridge, method).calls.filter {
        it.method.name == "setImageResource" && it.args.singleOrNull().isResource(resource)
    }.mapNotNull { it.receiver?.field }.distinctBy { it.descriptor }.single()
}
