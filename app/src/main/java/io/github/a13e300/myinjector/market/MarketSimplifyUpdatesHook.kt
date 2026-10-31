package io.github.a13e300.myinjector.market

import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookConstantIf
import io.github.a13e300.myinjector.arch.hookNopIf
import io.github.a13e300.myinjector.arch.setObj
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.bridge.Xposed
import io.github.a13e300.myinjector.logD
import org.json.JSONObject

/** 同时适配当前 APK 内新旧两套原生升级页，不修改其 A/B 实验或升级数据来源。 */
internal class MarketSimplifyUpdatesHook(
    private val enabled: () -> Boolean,
    private val failure: (Throwable) -> Unit,
) : IHook() {
    private val hooks = mutableListOf<Unhook>()
    @Volatile private var closed = false
    @Volatile private var failed = false
    private fun active() = !closed && !failed && enabled()

    override fun onHook() {
        val adSwitch = findClass("com.xiaomi.market.model.AdSwitch")
        val adapter = findClass("com.xiaomi.market.ui.UpdateListRvAdapter")
        val oldState = findClass("com.xiaomi.market.ui.UpdateListRvAdapter\$PageCollapseState")
        val oldExpand = oldState.getObjS("Expand")!!
        val manager = findClass("com.xiaomi.market.ui.update.UpdateComponentManager")
        val newState = findClass("com.xiaomi.market.ui.update.UpdateComponentManager\$PageCollapseState")
        val newExpand = newState.getObjS("Expand")!!
        val fragment = findClass("com.xiaomi.market.ui.update.UpdateListV2Fragment")
        val bool = Boolean::class.javaPrimitiveType!!
        val uiContext = findClass("com.xiaomi.market.ui.UIContext")
        val recommendations = listOf("isUpdateListRecommendSupported", "isUpdateGridRecommendSupported", "isUpdateAppRelatedRecommendSupported")
            .map { Xposed.findMethodExact(adSwitch, it, emptyArray()).also { method -> check(method.returnType == bool) } }
        val oldFold = Xposed.findMethodExact(adapter, "setCollapseRequired", arrayOf(bool))
        val oldSetState = Xposed.findMethodExact(adapter, "setPageCollapseState", arrayOf(oldState))
        val oldUpdate = Xposed.findMethodExact(adapter, "updateData", arrayOf(List::class.java))
        val oldRequiresFold = Xposed.findMethodExact(adapter, "getCollapseRequired", emptyArray())
        val oldOrchard = Xposed.findMethodExact(adapter, "shouldShowOrchardGuide", emptyArray())
        val oldRecommend = Xposed.findMethodExact(adapter, "setRecommendData", arrayOf(findClass("com.xiaomi.market.viewmodels.RecommendGroupResult")))
        val build = Xposed.findMethodExact(manager, "buildComponentList", arrayOf(ArrayList::class.java, uiContext))
        val setState = Xposed.findMethodExact(manager, "setPageCollapseState", arrayOf(newState))
        val refresh = Xposed.findMethodExact(fragment, "onRefreshPageData", arrayOf(List::class.java, bool, JSONObject::class.java))
        val buildPage = Xposed.findMethodExact(fragment, "buildComponentList", emptyArray())
        listOf("collapseRequired", "pageCollapseState").forEach { Xposed.findField(adapter, it) }
        Xposed.findField(manager, "pageCollapseState")
        listOf("mRecommendComponentList", "isNeedScrollToRecommendGroup").forEach { Xposed.findField(fragment, it) }
        try {
            recommendations.forEach { hooks += it.hookConstantIf(false, ::active) }
            hooks += oldFold.hookBefore { if (active()) it.args[0] = false }
            hooks += oldSetState.hookBefore { if (active()) it.args[0] = oldExpand }
            hooks += oldRequiresFold.hookConstantIf(false, ::active)
            hooks += oldOrchard.hookConstantIf(false, ::active)
            hooks += oldRecommend.hookNopIf(::active)
            hooks += oldUpdate.hookBefore { param -> safely {
                param.thisObject.setObj("collapseRequired", false)
                // isCollapseState() 实际在 Expand 时返回 true；保留原生判断，
                // updateData 才会使用全部数据，并将 foldButtonVisible 设为 false。
                param.thisObject.setObj("pageCollapseState", oldExpand)
            } }
            hooks += setState.hookBefore { if (active()) it.args[0] = newExpand }
            hooks += build.hook(
                before = { param -> safely { param.thisObject.setObj("pageCollapseState", newExpand) } },
                after = { param -> if (param.throwable == null) safely {
                    // 原生升级项、空态、存储不足提示及忽略记录保留；移除引导开启自动更新的插卡。
                    param.result = (param.result as List<*>).filterNot {
                        it?.javaClass?.name == "com.xiaomi.market.common.component.componentbeans.UpdateAutoDownloadComponent"
                    }.toMutableList()
                } },
            )
            hooks += buildPage.hookBefore { param -> safely {
                param.thisObject.setObj("mRecommendComponentList", arrayListOf<Any>())
                param.thisObject.setObj("isNeedScrollToRecommendGroup", false)
            } }
            hooks += refresh.hook(
                before = { param -> safely { param.args[0] = arrayListOf<Any>() } },
                after = { param -> if (param.throwable == null) safely {
                    // 请求缓存及网络响应共用该出口；重新用本地升级组件构建，避免旧推荐残留。
                    val nativeList = param.thisObject.call("buildComponentList")!!
                    param.thisObject.call("setDataList", nativeList)
                } },
            )
            logD("market simplified update page hooks installed")
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    private inline fun safely(block: () -> Unit) {
        if (!active()) return
        try { block() } catch (error: Throwable) { failed = true; failure(error) }
    }

    override fun onHookError(t: Throwable) { failure(t) }

    fun close() {
        closed = true
        hooks.asReversed().forEach { it.unhook() }
        hooks.clear()
    }
}
