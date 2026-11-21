package io.github.a13e300.myinjector.bili

import java.util.concurrent.ConcurrentHashMap

/** 只记录宿主实际读取或显示的结果，不把已保存选项当成观测结果。 */
internal class BiliInterfaceState(config: BiliHookConfig) {
    val activeModes = BiliInterfaceComponent.entries.associateWith { config.interfaceMode(it) }
    private val selections = ConcurrentHashMap<BiliInterfaceComponent, ConcurrentHashMap<String, Boolean>>()
    private val pages = ConcurrentHashMap<BiliInterfaceComponent, Set<Boolean>>()
    private val errors = ConcurrentHashMap.newKeySet<BiliInterfaceComponent>()

    fun selected(component: BiliInterfaceComponent, key: String, new: Boolean) {
        selections.computeIfAbsent(component) { ConcurrentHashMap() }[key] = new
    }

    fun displayed(component: BiliInterfaceComponent, new: Boolean) {
        pages.compute(component) { _, old -> old.orEmpty() + new }
    }

    fun failed(component: BiliInterfaceComponent) { errors += component }
    fun hasError(component: BiliInterfaceComponent): Boolean = component in errors

    fun summary(component: BiliInterfaceComponent, saved: BiliInterfaceMode): String = buildString {
        append("已保存：${saved.title}")
        if (saved != activeModes.getValue(component)) {
            append("（待重启；本次：${activeModes.getValue(component).title}）")
        }
        append("\n实际页面：${versions(pages[component].orEmpty())}")
        append("；入口选择：${versions(selections[component]?.values?.toSet().orEmpty())}")
        append("\n${component.description}；修改后重启生效")
        if (hasError(component)) append("\n存在问题，请查看 Hook 错误")
    }

    private fun versions(values: Set<Boolean>): String = when (values) {
        setOf(true) -> "新版"
        setOf(false) -> "旧版"
        setOf(true, false) -> "新版、旧版均已观测"
        else -> "未观测"
    }
}
