package io.github.a13e300.myinjector.xhs

import android.view.View
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

internal fun locateXhsHomeTabScroll(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val scrollAndRefresh = bridge.findMethod {
        matcher {
            returnType = "void"
            paramCount = 8
            usingEqStrings("scrollToTopAndRefresh, type=", ", source=", ", pinNoteId=")
        }
    }.single()
    val scroll = scrollAndRefresh.invokes.single {
        it.paramTypeNames == listOf(it.className, "boolean", "int", "java.lang.Object")
    }
    val helper = scrollAndRefresh.usingFields.map { it.field }
        .filter { it.className == scrollAndRefresh.className && it.typeName == scroll.className }
        .distinctBy { it.descriptor }.single()
    val recycler = scroll.declaredClass!!.fields.single {
        it.typeName == "androidx.recyclerview.widget.RecyclerView"
    }
    return mapOf(
        "scrollAndRefresh" to scrollAndRefresh.toObfsInfo(),
        "scroll" to scroll.toObfsInfo(),
        "helper" to helper.toObfsInfo(),
        "recycler" to recycler.toObfsInfo(),
    )
}

class HomeTabScrollFirst : MyDynHook("homeTabScrollFirst") {
    override fun isFeatureEnabled(): Boolean = XhsHandler.settings.homeTabScrollFirst

    override fun onHook() {
        val creator = XhsHandler.creator
        val targets by lazy { locateXhsHomeTabScroll(creator.bridge) }
        fun info(key: String) = creator.create("homeTabScrollFirst.$key") { targets.getValue(key) }
        fun method(key: String) = DexMethod(info(key).descriptor).getMethodInstance(classLoader)
            .apply { isAccessible = true }
        fun field(key: String) = DexField(info(key).descriptor).getFieldInstance(classLoader)
            .apply { isAccessible = true }

        val scroll = method("scroll")
        val helperField = field("helper")
        val recyclerField = field("recycler")
        method("scrollAndRefresh").hookBefore(cond = ::isEnabled) { param ->
            if ((param.args[3] as? Enum<*>)?.name != "CLICK_HOME_TAB_REFRESH") return@hookBefore
            val helper = helperField.get(param.args[0]) ?: return@hookBefore
            val recycler = recyclerField.get(helper) as View
            // 必须在回顶前判断，第一行只滚出一部分时也算未到顶部
            if (!recycler.canScrollVertically(-1)) return@hookBefore
            scroll.invoke(null, helper, false, 1, null)
            param.result = null
        }
    }
}
