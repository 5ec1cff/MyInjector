package io.github.a13e300.myinjector.bili

import android.app.Activity
import android.app.Dialog
import android.os.Bundle
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.deoptimize
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.bridge.Xposed
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method
import java.lang.reflect.Modifier

/** 界面开关只使用本次加载的配置，避免宿主的 Lazy 和已有页面混用两套结果。 */
internal class BiliInterfaceVersions(
    private val loader: ClassLoader,
    val state: BiliInterfaceState,
    private val onError: (BiliInterfaceComponent, String, Throwable) -> Unit,
) {
    private val decisions = BiliInterfaceComponent.entries.flatMap { component ->
        component.decisionKeys.map { it to component }
    }.toMap()
    private val decisionMethods = mutableSetOf<Method>()

    fun install(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        // 先安装配置读取 Hook，再加载可能缓存灰度结果的业务类。
        runCatching {
            installBooleanDecisions(loader.loadClass("com.bilibili.lib.dd.DeviceDecision"), hooks)
        }.onFailure { error -> decisions.values.toSet().forEach { fail(it, "DeviceDecision", error) } }
        runCatching {
            val dd = loader.loadClass("kntr.base.dd.IDeviceDecisionKt")
            val methods = dd.declaredMethods.filter { it.name in setOf("getBool", "getBool\$default", "getString", "getString\$default") }
            check(methods.size == 4) { "KDeviceDecision 读取方法不完整" }
            methods.forEach { method ->
                hooks += method.hook(before = { param ->
                    val key = param.args[1] as String
                    val component = decisions[key] ?: return@hook
                    state.activeModes.getValue(component).forcedValue?.let { new ->
                        param.result = if (key == "kmp_theseus") { if (new) "new" else "old" } else new
                    }
                }, after = { param ->
                    val key = param.args[1] as String
                    val component = decisions[key] ?: return@hook
                    if (param.throwable == null) {
                        state.selected(component, key, if (key == "kmp_theseus") param.result == "new" else param.result == true)
                    }
                })
            }
        }.onFailure { error ->
            listOf(BiliInterfaceComponent.COMMENTS, BiliInterfaceComponent.VIDEO).forEach { fail(it, "KDeviceDecision", error) }
        }
        BiliInterfaceComponent.entries.forEach { component ->
            attempt(component, "处理配置读取方") {
                component.decisionKeys.forEach { key ->
                    val readers = creator.bridge.findMethod { matcher { usingEqStrings(key) } }
                    check(readers.isNotEmpty()) { "找不到配置读取方：$key" }
                    readers.forEach(::deoptimizeReader)
                }
            }
        }
        attempt(BiliInterfaceComponent.SHARE, "分享框架选择") {
            val info = creator.create("newShare.frameworkEnabled", ::locateBiliNewShareEnabled)
            installSelector(BiliInterfaceComponent.SHARE, info, creator, hooks)
        }
        attempt(BiliInterfaceComponent.VIDEO, "视频框架选择") {
            installSelector(BiliInterfaceComponent.VIDEO,
                creator.create("interface.video.selector", ::locateBiliVideoVersion), creator, hooks)
        }
        attempt(BiliInterfaceComponent.MINE, "我的页面选择") {
            val info = creator.create("interface.mine.lazy", ::locateBiliMineVersion)
            val field = DexField(info.descriptor).getFieldInstance(loader).apply { isAccessible = true }
            creator.bridge.getFieldData(info.descriptor)!!.readers.forEach(::deoptimizeReader)
            val lazy = checkNotNull(field.get(null))
            // 只覆盖这个 Lazy 的值，不能直接修改被 R8 合并的 lambda.invoke。
            val getter = lazy.javaClass.getDeclaredMethod("getValue")
            hooks += getter.hook(before = { param ->
                if (param.thisObject === lazy) {
                    state.activeModes.getValue(BiliInterfaceComponent.MINE).forcedValue?.let { param.result = it }
                }
            }, after = { param ->
                if (param.thisObject === lazy && param.throwable == null) {
                    state.selected(BiliInterfaceComponent.MINE, "final", param.result == true)
                }
            })
        }
        attempt(BiliInterfaceComponent.SPLASH, "开屏资源选择") {
            // 开屏资源工厂直接使用 GDeviceDecision，绕过 DeviceDecision 包装类。
            val info = creator.create("interface.splash.producer", ::locateBiliSplashProducer)
            hooks += method(info).hookBefore { param ->
                attempt(BiliInterfaceComponent.SPLASH, "开屏配置实例") {
                    installBooleanDecisions(param.args[1]!!.javaClass, hooks)
                }
            }
        }
        installPageObservers(creator, hooks)
    }

    @Synchronized
    private fun installBooleanDecisions(type: Class<*>, hooks: MutableList<Unhook>) {
        val methods = type.methods.filter { it.name == "getBoolean" &&
            it.parameterTypes.firstOrNull() == String::class.java &&
            it.returnType == Boolean::class.javaPrimitiveType && !Modifier.isAbstract(it.modifiers) }
        check(methods.isNotEmpty()) { "找不到 ${type.name}.getBoolean" }
        methods.filter { decisionMethods.add(it) }.forEach { method ->
            hooks += method.hook(before = { param ->
                val component = decisions[param.args[0]] ?: return@hook
                state.activeModes.getValue(component).forcedValue?.let { param.result = it }
            }, after = { param ->
                val key = param.args[0] as String
                val component = decisions[key] ?: return@hook
                // 「我的」还包含设备类型判断，由最终 Lazy 记录入口选择。
                if (param.throwable == null && component != BiliInterfaceComponent.MINE) {
                    state.selected(component, key, param.result == true)
                }
            })
        }
    }

    private fun installSelector(component: BiliInterfaceComponent, info: ObfsInfo, creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        hooks += method(info).hook(before = { param ->
            state.activeModes.getValue(component).forcedValue?.let { param.result = it }
        }, after = { param ->
            if (param.throwable == null) state.selected(component, "final", param.result == true)
        })
        creator.bridge.getMethodData(info.descriptor)!!.callers.forEach(::deoptimizeReader)
    }

    private fun installPageObservers(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        runCatching {
            hooks += Activity::class.java.getDeclaredMethod("onCreate", Bundle::class.java).hookAfter { param ->
                if (param.throwable == null) observePage(param.thisObject.javaClass)
            }
            hooks += loader.loadClass("androidx.fragment.app.Fragment").getDeclaredMethod("performViewCreated").hookAfter { param ->
                if (param.throwable == null) observePage(param.thisObject.javaClass)
            }
        }.onFailure { error -> BiliInterfaceComponent.entries.forEach { fail(it, "页面观测", error) } }
        attempt(BiliInterfaceComponent.SHARE, "旧版分享观测") {
            hooks += Dialog::class.java.getDeclaredMethod("show").hookAfter { param ->
                if (param.throwable == null && (param.thisObject as Dialog).isShowing &&
                    Thread.currentThread().stackTrace.any {
                        it.className.startsWith("com.bilibili.app.comm.supermenu.share.v2.") ||
                            it.className.startsWith("com.bilibili.lib.sharewrapper.selector.DialogSharePlatformSelector")
                    }) {
                    state.displayed(BiliInterfaceComponent.SHARE, false)
                }
            }
        }
        val traces = listOf(
            BiliInterfaceComponent.SHARE to "kntr.common.share.api.ShareFacade.ShareMenu (ShareFacade.kt:384)",
            BiliInterfaceComponent.SEARCH to "kntr.app.search.SearchDerivedShell (SearchDerivedShell.kt:102)",
            BiliInterfaceComponent.SEARCH_SUBPAGE to "kntr.app.search.comic.page.SearchComicPage (SearchComicPage.kt:61)",
            BiliInterfaceComponent.SEARCH_SUBPAGE to "kntr.app.search.aggregate.page.SearchAggregatePage (SearchAggregatePage.kt:75)",
            BiliInterfaceComponent.SEARCH_SUBPAGE to "kntr.app.search.episode.page.SearchEpisodePage (SearchEpisodePage.kt:107)",
            BiliInterfaceComponent.OPUS to "kntr.app.following.opusDetail.ui.chrome.OpusDetailNavBar.<anonymous>.<anonymous> (OpusDetailNavBar.kt:160)",
            BiliInterfaceComponent.HISTORY to "kntr.home.history.ui.HistoryRootScreen (HistoryRootScreen.kt:120)",
            BiliInterfaceComponent.COMMENTS to "kntr.common.comment.page.ui.CommentPage (CommentPage.kt:80)",
        )
        traces.forEachIndexed { index, (component, trace) ->
            attempt(component, "新版页面观测") {
                val info = creator.create("interface.${component.key}.page.$index") { locateBiliInterfaceTrace(it, trace) }
                hooks += method(info).hookAfter { param ->
                    if (param.throwable == null) state.displayed(component, true)
                }
            }
        }
        attempt(BiliInterfaceComponent.VIDEO, "新版视频观测") {
            hooks += method(creator.create("interface.video.created", ::locateBiliNewVideoCreated)).hookAfter { param ->
                if (param.throwable == null) state.displayed(BiliInterfaceComponent.VIDEO, true)
            }
        }
    }

    private fun observePage(type: Class<*>) {
        var parent: Class<*>? = type
        while (parent != null) {
            if (parent.name == "tv.danmaku.bili.splash.ad.page.BaseSplash") {
                state.displayed(BiliInterfaceComponent.SPLASH, false)
                return
            }
            parent = parent.superclass
        }
        val name = type.name
        val version = when (name) {
            "com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity" -> BiliInterfaceComponent.VIDEO to false
            "com.bilibili.search2.main.BiliMainSearchActivity" -> BiliInterfaceComponent.SEARCH to false
            "com.bilibili.search2.comic.SearchComicFragment",
            "com.bilibili.search2.result.bangumi.SearchBangumiEpisodeGridFragment",
            "com.bilibili.search2.result.bangumi.SearchBangumiEpisodeHorizontalFragment",
            "com.bilibili.search2.result.ogv.aggregate.subpage.SearchAggregateFragment" -> BiliInterfaceComponent.SEARCH_SUBPAGE to false
            "com.bilibili.bplus.followinglist.page.opus.OpusDetailFragment",
            "com.bilibili.bplus.followinglist.detail.DynamicDetailFragment" -> BiliInterfaceComponent.OPUS to false
            "com.bilibili.bplus.followinglist.home.FollowingListCmpRootFragment" -> BiliInterfaceComponent.FOLLOWING to true
            "com.bilibili.bplus.followinglist.home.DynamicHomeTabFragment" -> BiliInterfaceComponent.FOLLOWING to false
            "com.bilibili.app.history.HistoryFragmentV3" -> BiliInterfaceComponent.HISTORY to false
            "kntr.srcs.app.splash.startup.SplashContainerFragment" -> BiliInterfaceComponent.SPLASH to true
            "tv.danmaku.bili.ui.main2.mineV2.HomeUserCenterFragmentV2" -> BiliInterfaceComponent.MINE to true
            "tv.danmaku.bili.ui.main2.mine.HomeUserCenterFragment" -> BiliInterfaceComponent.MINE to false
            "com.bilibili.app.comm.comment2.comments.view.MainCommentListFragment",
            "com.bilibili.app.comm.comment2.comments.view.PrimaryCommentMainFragment",
            "com.bilibili.app.comm.comment2.comments.view.CommentDetailFragment",
            "com.bilibili.app.comm.comment2.comments.view.CommentDialogueFragment",
            "com.bilibili.app.comment3.CommentV3Fragment",
            "com.bilibili.app.comment3.ui.CommentV3MainFragment",
            "com.bilibili.app.comment3.ui.CommentV3DetailFragment",
            "com.bilibili.app.comment3.ui.CommentV3DialogFragment" -> BiliInterfaceComponent.COMMENTS to false
            else -> null
        }
        version?.let { state.displayed(it.first, it.second) }
    }

    private fun method(info: ObfsInfo): Method = DexMethod(info.descriptor).getMethodInstance(loader)
    private fun deoptimizeReader(reader: MethodData) {
        if (reader.isStaticInitializer) return
        val dex = DexMethod(reader.descriptor)
        if (reader.isConstructor) Xposed.deoptimizeMethod(dex.getConstructorInstance(loader))
        else dex.getMethodInstance(loader).deoptimize()
    }
    private inline fun attempt(component: BiliInterfaceComponent, stage: String, block: () -> Unit) {
        runCatching(block).onFailure { fail(component, stage, it) }
    }
    private fun fail(component: BiliInterfaceComponent, stage: String, error: Throwable) {
        state.failed(component)
        onError(component, stage, error)
    }
}
