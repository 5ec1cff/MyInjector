package io.github.a13e300.myinjector.telegram

import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import io.github.a13e300.myinjector.arch.hookAfter
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexMethod

class FixAccessibilityNodeClassName : MyDynHook("fixAccessibilityNodeClassName") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.run {
        !hasFixAccessibilityNodeClassName() || fixAccessibilityNodeClassName
    }

    override fun onHook() {
        val providers = findAccessibilityNodeProviders(TelegramHandler.creator.bridge)
        for (descriptor in providers.values) {
            DexMethod(descriptor).getMethodInstance(classLoader).hookAfter(cond = ::isEnabled) { param ->
                if (param.throwable != null) return@hookAfter
                val node = param.result as? AccessibilityNodeInfo ?: return@hookAfter
                fillAccessibilityNodeClassName(node)
            }
        }
    }
}

internal fun fillAccessibilityNodeClassName(node: AccessibilityNodeInfo) {
    // 提供器返回时节点尚未 sealed，只补缺失的类名。
    if (node.className == null) node.className = View::class.java.name
}

internal fun findAccessibilityNodeProviders(bridge: DexKitBridge): Map<String, String> {
    return bridge.findMethod {
        matcher {
            name("createAccessibilityNodeInfo")
            paramTypes("int")
            returnType("android.view.accessibility.AccessibilityNodeInfo")
            declaredClass { superClass("android.view.accessibility.AccessibilityNodeProvider") }
        }
    }.filter { it.className.startsWith("org.telegram.") }
        .associate { it.className to it.descriptor }
        .also { check(it.isNotEmpty()) { "Telegram accessibility node providers not found" } }
}
