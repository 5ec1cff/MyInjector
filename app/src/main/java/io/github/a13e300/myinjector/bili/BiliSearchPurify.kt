package io.github.a13e300.myinjector.bili

import android.view.View
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.findBaseActivityOrNull
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookConstantIf
import io.github.a13e300.myinjector.bridge.Unhook
import org.luckypray.dexkit.wrap.DexMethod

/** 保留搜索历史和输入联想，只去掉搜索发现页的推荐内容及默认推荐词。 */
internal class BiliSearchPurify(
    private val loader: ClassLoader,
    private val enabled: () -> Boolean,
    private val onError: (String, Throwable) -> Unit,
) {
    fun install(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        runCatching { installLegacy(hooks) }.onFailure { onError("安装旧版搜索净化", it) }
        runCatching { installLegacyHint(creator, hooks) }.onFailure { onError("安装旧版搜索提示", it) }
        runCatching { installNew(creator, hooks) }.onFailure { onError("安装新版搜索净化", it) }
    }

    private fun installLegacy(hooks: MutableList<Unhook>) {
        // 读取时隐藏，不改写缓存对象；关闭开关后后续读取恢复原值。
        val square = loader.loadClass("com.bilibili.search2.api.SearchSquareType")
        hooks += square.getMethod("getType").hookAfter { param ->
            if (param.throwable == null && enabled() && param.result != "history") param.result = ""
        }
        val referral = loader.loadClass("com.bilibili.search2.api.SearchReferral")
        hooks += referral.getMethod("getList").hookConstantIf(emptyList<Any>(), enabled)
        hooks += referral.getMethod("getTitle").hookConstantIf("", enabled)
        hooks += referral.getMethod("getPages").hookConstantIf(0, enabled)
        val word = loader.loadClass("com.bilibili.search2.api.DefaultKeyword")
        word.declaredMethods.filter {
            it.name.startsWith("get") && it.parameterCount == 0 && it.returnType == String::class.java
        }.forEach { getter -> hooks += getter.hookConstantIf(null, enabled) }
        hooks += word.getMethod("getShowFront").hookConstantIf(0, enabled)
        for (name in listOf("enableRefresh", "enableAnimation")) {
            hooks += word.getMethod(name).hookConstantIf(false, enabled)
        }
    }

    private fun installLegacyHint(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        val search = loader.loadClass("tv.danmaku.bili.widget.SearchView")
        val bar = loader.loadClass("com.bilibili.search2.ogv.OgvSearchView")
        val activity = loader.loadClass("com.bilibili.search2.main.BiliMainSearchActivity")
        val teenagers = loader.loadClass("com.bilibili.teenagersmode.TeenagersMode")
        val instance = teenagers.getMethod("getInstance")
        val isMinor = teenagers.getMethod("isEnable")
        hooks += search.getMethod("setQueryHint", CharSequence::class.java).hookBefore { param ->
            if (!enabled() || !bar.isInstance(param.thisObject)) return@hookBefore
            val view = param.thisObject as View
            if (!activity.isInstance(view.context.findBaseActivityOrNull())) return@hookBefore
            runCatching {
                // 旧版观察回调会把 null 转为 ""，覆盖 XML 提示，必须在写入搜索框时恢复。
                val key = if (isMinor.invoke(instance.invoke(null)) == true) "search_global_string_201"
                    else "search_global_string_72"
                val id = view.resources.getIdentifier(key, "string", view.context.packageName)
                check(id != 0) { "找不到搜索提示资源: $key" }
                param.args[0] = view.context.getString(id)
            }.onFailure { onError("恢复旧版搜索提示", it) }
        }
        val observer = creator.create("searchPurify.legacyHintObserver", ::locateBiliLegacySearchHintObserver)
        // setQueryHint 很短，解除调用方的内联，确保后续更新经过 Hook。
        DexMethod(observer.descriptor).getMethodInstance(loader).deoptimize()
    }

    private fun installNew(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        val members = biliSearchPurifyMembers(creator)
        fun method(key: String) = DexMethod(members.getValue(key).descriptor).getMethodInstance(loader)
            .apply { isAccessible = true }
        val history = loader.loadClass(members.getValue("history").className)
        hooks += method("square").hookAfter { param ->
            if (param.throwable != null || !enabled()) return@hookAfter
            runCatching {
                // 协程首次返回可能是 COROUTINE_SUSPENDED，最终结果在恢复后再过滤。
                val sections = param.result as? List<*> ?: return@runCatching
                val kept = sections.filter { history.isInstance(it) }
                if (kept.size != sections.size) param.result = kept
            }.onFailure { onError("过滤新版搜索板块", it) }
        }
        hooks += method("recommend").hookAfter { param ->
            if (param.throwable == null && enabled() && param.result is List<*>) param.result = emptyList<Any>()
        }
        val word = loader.loadClass(members.getValue("wordModel").className)
        hooks += method("defaultWord").hookAfter { param ->
            if (param.throwable == null && enabled() && word.isInstance(param.result)) param.result = null
        }
        // 路由、缓存和首页响应也可带默认词，单独处理子 serializer，覆盖嵌套及可空解析。
        // 在解码完成后替换，不能提前返回，否则共享 Decoder 会停在旧位置。
        for ((modelKey, decodeKey) in listOf("wordModel" to "wordDeserialize", "homeWordModel" to "homeWordDeserialize")) {
            val model = loader.loadClass(members.getValue(modelKey).className)
            hooks += method(decodeKey).hookAfter { param ->
                if (param.throwable == null && enabled() && model.isInstance(param.result)) param.result = null
            }
        }
        // 首页缺少默认词时，宿主也会合成空模型。展示状态必须为 null 才会使用固定提示。
        for (key in listOf("pageState", "discoverContent")) {
            val constructor = DexMethod(members.getValue(key).descriptor).getConstructorInstance(loader)
            val index = constructor.parameterTypes.indexOf(word)
            check(index >= 0) { "搜索状态缺少默认词参数: $constructor" }
            hooks += constructor.hookBefore { param ->
                if (enabled()) param.args[index] = null
            }
        }
    }
}
