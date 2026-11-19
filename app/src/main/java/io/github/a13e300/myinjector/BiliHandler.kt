package io.github.a13e300.myinjector

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.AtomicFile
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookConstantIf
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.bili.BiliCommentLink
import io.github.a13e300.myinjector.bili.BiliCommentRoute
import io.github.a13e300.myinjector.bili.BiliSubtitles
import io.github.a13e300.myinjector.bili.BiliSubtitleToolbar
import io.github.a13e300.myinjector.bili.BiliCover
import io.github.a13e300.myinjector.bili.BiliAvHeadline
import io.github.a13e300.myinjector.bili.biliAvHeadlineMembers
import io.github.a13e300.myinjector.bili.biliAvSelector
import io.github.a13e300.myinjector.bili.BiliFeature
import io.github.a13e300.myinjector.bili.BiliHookConfig
import io.github.a13e300.myinjector.bili.BiliSettingsDialog
import io.github.a13e300.myinjector.bili.BiliSettingsEntry
import io.github.a13e300.myinjector.bili.biliCoverMembers
import io.github.a13e300.myinjector.bili.biliCommentLinkMembers
import io.github.a13e300.myinjector.bili.biliCommentRouteMembers
import io.github.a13e300.myinjector.bili.biliSubtitleMembers
import io.github.a13e300.myinjector.bili.BiliNewShare
import io.github.a13e300.myinjector.bili.biliNewShareMembers
import io.github.a13e300.myinjector.bili.locateBiliActivityMetaField
import io.github.a13e300.myinjector.bili.locateBiliFreeDataGuides
import io.github.a13e300.myinjector.bili.locateBiliNewSplash
import io.github.a13e300.myinjector.bili.locateBiliNewShareEnabled
import io.github.a13e300.myinjector.bili.locateBiliPausePageConfig
import io.github.a13e300.myinjector.bili.locateBiliShare
import io.github.a13e300.myinjector.bili.locateBiliSplash
import io.github.a13e300.myinjector.bili.locateBiliUnderPlayerAds
import io.github.a13e300.myinjector.bridge.Unhook
import org.json.JSONObject
import org.luckypray.dexkit.wrap.DexMethod
import java.io.File
import java.io.FileNotFoundException
import java.util.concurrent.ConcurrentHashMap

class BiliHandler : IHook() {
    private val hooks = mutableListOf<Unhook>()
    private lateinit var configFile: AtomicFile
    @Volatile var settings = BiliHookConfig()
        private set
    private val hookErrors = ConcurrentHashMap<Pair<BiliFeature?, String>, String>()

    private fun readSettings() {
        configFile = AtomicFile(File(loadPackageParam.appInfo.dataDir, "my_injector_settings"))
        settings = runCatching {
            configFile.openRead().bufferedReader(Charsets.UTF_8).use {
                BiliHookConfig.readFromJson(JSONObject(it.readText()))
            }
        }.onFailure {
            if (it !is FileNotFoundException || configFile.baseFile.exists()) {
                reportError(null, "读取配置", it)
            }
        }.getOrDefault(BiliHookConfig())
    }

    @Synchronized
    fun updateSettings(config: BiliHookConfig) {
        runCatching {
            val bytes = config.toJson().toString().toByteArray(Charsets.UTF_8)
            val output = configFile.startWrite()
            try {
                output.write(bytes)
                configFile.finishWrite(output)
            } catch (t: Throwable) {
                configFile.failWrite(output)
                throw t
            }
            // AtomicFile.finishWrite 只记录部分提交错误，读回确认后才接受新的开关状态。
            check(configFile.openRead().use { it.readBytes() }.contentEquals(bytes)) { "JSON 配置未保存成功" }
            // 保存成功后发布新配置，后续操作直接读取，保留已显示菜单的点击回调。
            settings = config
        }.onFailure { reportError(null, "保存配置", it) }.getOrThrow()
    }

    internal fun reportError(feature: BiliFeature?, stage: String, error: Throwable) {
        logE("bili ${feature?.key ?: "settings"}: $stage", error)
        hookErrors[feature to stage] = error.stackTraceToString()
    }

    internal fun hasError(feature: BiliFeature): Boolean = hookErrors.keys.any { it.first == feature }

    internal fun errorReport(): String = hookErrors.entries
        .sortedWith(compareBy({ it.key.first?.ordinal ?: -1 }, { it.key.second }))
        .joinToString("\n\n") { (key, trace) -> "${key.first?.title ?: "设置与初始化"} [${key.second}]\n$trace" }

    private fun hookShare(creator: ObfsTableCreator) {
        val b23TvRegex = Regex("https?://b23\\.tv/[0-9a-zA-Z]+")
        val filterOutParams = listOf("share_session_id", "share_source", "bbid", "share_medium")
        runCatching {
            // 单参数 shareTo 会在 delegate 内生成 Bundle；两条分享路径最终都到达此方法。
            val info = creator.create("shareWithContent", ::locateBiliShare)
            hooks += DexMethod(info.descriptor).getMethodInstance(classLoader).hookBefore { param ->
                runCatching {
                    if (!settings.isEnabled(BiliFeature.BETTER_SHARE)) return@hookBefore
                    val b = param.args[1] as? Bundle ?: return@hookBefore
                    val content = b.getString("params_content") ?: return@hookBefore
                    val url = b.getString("params_target_url") ?: return@hookBefore
                    val res = b23TvRegex.find(content) ?: return@hookBefore
                    val uri = Uri.parse(url)
                    if (uri.scheme !in listOf("http", "https")) return@hookBefore
                    val sanitizedUrl = uri.run {
                        val newUrl = buildUpon().clearQuery()
                        queryParameterNames.forEach {
                            if (it !in filterOutParams) {
                                newUrl.appendQueryParameter(it, getQueryParameter(it))
                            }
                        }
                        newUrl.build().toString()
                    }
                    logD("url $url -> $sanitizedUrl")
                    b.putString("params_content", content.replaceRange(res.range, sanitizedUrl))
                }.onFailure { reportError(BiliFeature.BETTER_SHARE, "替换旧版分享链接", it) }
            }
            logD("bili share content filter installed: ${info.descriptor}")
        }.onFailure {
            reportError(BiliFeature.BETTER_SHARE, "hookShare", it)
        }
    }

    private fun hookAd(creator: ObfsTableCreator) {
        runCatching {
            val renderers = creator.obfsTable.filterKeys { it.startsWith("underPlayerAd_") }.values
                .ifEmpty {
                    locateBiliUnderPlayerAds(creator.bridge).also { methods ->
                        methods.forEachIndexed { i, method -> creator.obfsTable["underPlayerAd_$i"] = method }
                        creator.changed = true
                    }
                }
            renderers.forEach { info ->
                val method = DexMethod(info.descriptor).getMethodInstance(classLoader)
                val result = if (method.returnType == Void.TYPE) null
                    else findClass("kotlin.Unit").getObjS("INSTANCE")!!
                // 正常完成本次广告 Flow 回调；不返回 COROUTINE_SUSPENDED，也不使用 null 冒充 Unit。
                hooks += method.hookConstantIf(result) { settings.isEnabled(BiliFeature.REMOVE_UNDER_PLAYER_AD) }
                logD("bili under-player ad renderer blocked: ${info.descriptor}")
            }
        }.onFailure {
            reportError(BiliFeature.REMOVE_UNDER_PLAYER_AD, "hookAd", it)
        }
    }

    private fun hookShowAv(creator: ObfsTableCreator) {
        listOf("com.bilibili.droid.BVCompat", "kntr.base.utils.compat.BVCompat").forEach { name ->
            runCatching {
                val selector = biliAvSelector(findClass(name))
                // 两个参数分别是已格式化的 AV 和 BV，直接选用宿主提供的 AV 文案。
                hooks += selector.hookBefore { param ->
                    if (settings.isEnabled(BiliFeature.SHOW_AV)) param.result = param.args[0]
                }
                logI("bili AV selector installed: $selector")
            }.onFailure { reportError(BiliFeature.SHOW_AV, "AV 选择器 ($name)", it) }
        }
        runCatching {
            val headline = BiliAvHeadline(classLoader, biliAvHeadlineMembers(creator))
            hooks += headline.render.hook(before = { param ->
                runCatching { headline.scope.enter(param.thisObject, settings.isEnabled(BiliFeature.SHOW_AV)) }
                    .onFailure { reportError(BiliFeature.SHOW_AV, "读取新版视频 ID", it) }
            }, after = { headline.scope.leave() })
            hooks += headline.item.hookBefore { param ->
                param.args[0] = headline.scope.text(param.args[0])
            }
            logI("bili AV headline installed: ${headline.render}")
        }.onFailure { reportError(BiliFeature.SHOW_AV, "新版标题区显示 AV", it) }
    }

    private fun hookNewShare(creator: ObfsTableCreator) {
        runCatching {
            val share = BiliNewShare(classLoader, biliNewShareMembers(creator))
            hooks += share.contentProvider.hookAfter { param ->
                if (param.throwable != null || !settings.isEnabled(BiliFeature.BETTER_SHARE)) return@hookAfter
                runCatching {
                    val content = share.localContent(param.thisObject, param.args[0]!!, param.result)
                    if (content !== param.result) param.result = content
                }.onFailure { reportError(BiliFeature.BETTER_SHARE, "生成分享内容", it) }
            }
            hooks += share.engine.hookBefore { param ->
                if (!settings.isEnabled(BiliFeature.BETTER_SHARE)) return@hookBefore
                runCatching {
                    if (share.useLocalCopy(param.args)) logD("bili copy original link without share fetch")
                }.onFailure { reportError(BiliFeature.BETTER_SHARE, "复制原链接", it) }
            }
            logD("bili new share local copy installed: ${share.engine}")
        }.onFailure { reportError(BiliFeature.BETTER_SHARE, "hookNewShare", it) }
    }

    private fun hookForceNewShare(creator: ObfsTableCreator) {
        runCatching {
            val enabled = creator.create("newShare.frameworkEnabled", ::locateBiliNewShareEnabled)
            // 启用时覆盖入口返回值，关闭后让宿主读取原有的灰度结果。
            hooks += DexMethod(enabled.descriptor).getMethodInstance(classLoader).hookConstantIf(true) { settings.isEnabled(BiliFeature.FORCE_NEW_SHARE) }
            logD("bili new share framework forced: ${enabled.descriptor}")
        }.onFailure { reportError(BiliFeature.FORCE_NEW_SHARE, "固定新版分享框架", it) }
    }

    private fun hookCommentLink(creator: ObfsTableCreator) {
        runCatching {
            val link = BiliCommentLink(classLoader, biliCommentLinkMembers(creator)) {
                reportError(BiliFeature.COPY_COMMENT_LINK, "复制评论链接", it)
            }
            hooks += link.menu.hook(before = { param ->
                runCatching { link.enter(param.args, settings.isEnabled(BiliFeature.COPY_COMMENT_LINK)) }.onFailure { reportError(BiliFeature.COPY_COMMENT_LINK, "读取评论信息", it) }
            }, after = { link.leave() })
            link.renderers.forEach { renderer ->
                val listIndex = renderer.parameterTypes.indexOf(List::class.java)
                hooks += renderer.hookBefore { param ->
                    runCatching { link.addEntry(param.args, listIndex) }
                        .onFailure { reportError(BiliFeature.COPY_COMMENT_LINK, "插入评论菜单", it) }
                }
            }
            logD("bili comment copy link installed: ${link.menu}")
        }.onFailure { reportError(BiliFeature.COPY_COMMENT_LINK, "hookCommentLink", it) }
    }

    private fun hookCommentLinkInVideo(creator: ObfsTableCreator) {
        runCatching {
            val route = BiliCommentRoute(classLoader, biliCommentRouteMembers(creator))
            hooks += route.legacy.hookBefore { param ->
                if (!settings.isEnabled(BiliFeature.COMMENT_LINK_IN_VIDEO)) return@hookBefore
                runCatching { param.args[0] = route.wrapLegacy(param.args[0]!!) }
                    .onFailure { reportError(BiliFeature.COMMENT_LINK_IN_VIDEO, "评论路由 (Theseus)", it) }
            }
            hooks += route.theseus2.hookBefore { param ->
                if (!settings.isEnabled(BiliFeature.COMMENT_LINK_IN_VIDEO)) return@hookBefore
                runCatching { param.args[0] = route.wrapTheseus2(param.args[0]!!) }
                    .onFailure { reportError(BiliFeature.COMMENT_LINK_IN_VIDEO, "评论路由 (Theseus2)", it) }
            }
            logD("bili comment links stay in video: ${route.legacy}, ${route.theseus2}")
        }.onFailure { reportError(BiliFeature.COMMENT_LINK_IN_VIDEO, "hookCommentLinkInVideo", it) }
    }

    private fun hookViewActions(creator: ObfsTableCreator) {
        val features = listOf(BiliFeature.VIEW_SUBTITLES, BiliFeature.VIEW_COVER)
        val members = runCatching { biliSubtitleMembers(creator) }.onFailure { error ->
            features.forEach { reportError(it, "定位视频操作菜单", error) }
        }.getOrNull() ?: return
        // 服务关联和点击处理常驻，关闭开关只停止向新菜单插入按钮。
        val subtitles = runCatching { hookViewSubtitles(members) }
            .onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "安装字幕 Hook", it) }.getOrNull()
        val cover = runCatching {
            BiliCover(classLoader, biliCoverMembers(creator), members.getValue("toolbarContainer")).also { cover ->
                hooks += cover.onCreate.hookAfter { param ->
                    if (param.throwable == null) runCatching { cover.onCreated(param.thisObject as Activity) }
                        .onFailure { reportError(BiliFeature.VIEW_COVER, "显示封面保存按钮", it) }
                }
            }
        }.onFailure { reportError(BiliFeature.VIEW_COVER, "安装封面 Hook", it) }.getOrNull()
        if (subtitles == null && cover == null) return
        runCatching {
            BiliSubtitleToolbar(classLoader, members, subtitles, cover,
                { settings.isEnabled(it) }, ::reportError).install(hooks)
        }.onFailure { error -> features.forEach { reportError(it, "安装视频操作菜单", error) } }
    }

    private fun hookViewSubtitles(members: Map<String, ObfsInfo>): BiliSubtitles {
        val subtitles = BiliSubtitles(classLoader, members) {
            reportError(BiliFeature.VIEW_SUBTITLES, "打开字幕弹窗", it)
        }
        hooks += subtitles.subtitleService.hookAllCAfter { param ->
            if (param.throwable == null) runCatching { subtitles.attach(param.thisObject) }
                .onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "bili subtitle service", it) }
        }
        val sharingBundle = findClass("tv.danmaku.biliplayerv2.PlayerSharingBundle")
        hooks += subtitles.legacyService.getMethod("onStart", sharingBundle).hookAfter { param ->
            if (param.throwable == null) runCatching { subtitles.attachLegacy(param.thisObject) }
                .onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "bili legacy subtitle service", it) }
        }
        val legacyReply = findClass("com.bapis.bilibili.subtitle.SubtitleViewReply")
        hooks += subtitles.legacyService.getMethod("setSubtitleReply", legacyReply, Boolean::class.javaPrimitiveType)
            .hookAfter { param ->
                if (param.throwable == null) runCatching { subtitles.attachLegacy(param.thisObject) }
                    .onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "bili legacy subtitle reply", it) }
            }
        subtitles.menuMethods.forEach { menu ->
            hooks += menu.hookAfter { param ->
                if (param.throwable != null || !settings.isEnabled(BiliFeature.VIEW_SUBTITLES)) return@hookAfter
                val callback = subtitles.menuCallback(param.thisObject) ?: return@hookAfter
                runCatching {
                    @Suppress("UNCHECKED_CAST")
                    val groups = param.result as List<List<Any>>
                    param.result = subtitles.insert(callback, groups)
                }.onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "bili subtitle menu", it) }
            }
        }
        subtitles.clickMethods.forEach { click ->
            hooks += click.hookBefore { param ->
                runCatching {
                    if (subtitles.onClick(param.thisObject, param.args[0]!!)) param.result = true
                }.onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "点击字幕按钮", it) }
            }
        }
        hooks += subtitles.filter.hookBefore { param ->
            runCatching { subtitles.allowChannels(param.args) }.onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "bili subtitle channel", it) }
        }
        subtitles.dismissMethods.forEach { dismiss ->
            hooks += dismiss.hookAfter { param ->
                val callback = subtitles.dismissCallback(param.thisObject) ?: return@hookAfter
                runCatching { subtitles.onDismiss(callback) }.onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "bili subtitle dialog", it) }
            }
        }
        hooks += subtitles.content.hook(before = { param ->
            runCatching { subtitles.enterItem(param.args[0]!!) }
                .onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "读取字幕按钮", it) }
        },
            after = { subtitles.leaveItem() })
        hooks += subtitles.clickable.hook(before = { param ->
            runCatching { subtitles.disableClick(param.args) }
                .onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "禁用字幕按钮", it) }
        }, after = { param ->
            if (param.throwable == null) runCatching {
                val dimmed = subtitles.dim(param.result)
                if (dimmed !== param.result) param.result = dimmed
            }.onFailure { reportError(BiliFeature.VIEW_SUBTITLES, "显示字幕禁用状态", it) }
        })
        logI("bili view subtitles installed: ${subtitles.menuMethods}")
        return subtitles
    }

    private fun hookPauseAd(creator: ObfsTableCreator) {
        // Disable the pause-ad flow before it requests an ad or starts the countdown.
        runCatching {
            hooks += findClass("com.bilibili.adcommon.config.DDConfig")
                .hookConstantIf("getPausePageEnable", constant = false) { settings.isEnabled(BiliFeature.REMOVE_PAUSE_AD) }
        }.onFailure { reportError(BiliFeature.REMOVE_PAUSE_AD, "hookPauseAd (Theseus)", it) }
        if (findClassOrNull("com.bilibili.ship.theseus2.united.page.pausedpage.PausedPageController") != null) {
            runCatching {
                val info = creator.create("pausePageConfig", ::locateBiliPausePageConfig)
                hooks += DexMethod(info.descriptor).getMethodInstance(classLoader).hookConstantIf(false) { settings.isEnabled(BiliFeature.REMOVE_PAUSE_AD) }
                logD("bili pause ad config blocked: ${info.descriptor}")
            }.onFailure { reportError(BiliFeature.REMOVE_PAUSE_AD, "hookPauseAd (Theseus2)", it) }
        }
    }

    private fun hookCloudTvOverlay(creator: ObfsTableCreator) {
        runCatching { hookLegacyCloudTvOverlay() }.onFailure { reportError(BiliFeature.REMOVE_CLOUD_TV_OVERLAY, "hookCloudTvOverlay (Theseus)", it) }
        val kotlinReply = findClassOrNull("com.bapis.bilibili.community.service.dm.v1.KDmViewReply") ?: return
        runCatching {
            val info = creator.create("activityMetaField", ::locateBiliActivityMetaField)
            val field = kotlinReply.getDeclaredField(info.memberName).apply { isAccessible = true }
            hooks += kotlinReply.hookAllCAfter { param ->
                if (param.throwable != null || !settings.isEnabled(BiliFeature.REMOVE_CLOUD_TV_OVERLAY)) return@hookAllCAfter
                runCatching {
                    val metas = field.get(param.thisObject) as? List<*> ?: return@hookAllCAfter
                    val kept = filterActivityMeta(metas)
                    if (kept !== metas) field.set(param.thisObject, kept)
                }.onFailure { reportError(BiliFeature.REMOVE_CLOUD_TV_OVERLAY, "filterCloudTvOverlay (Theseus2)", it) }
            }
            logD("bili Kotlin activity metadata filtered: ${info.descriptor}")
        }.onFailure { reportError(BiliFeature.REMOVE_CLOUD_TV_OVERLAY, "hookCloudTvOverlay (Theseus2)", it) }
    }

    private fun hookFreeDataGuide(creator: ObfsTableCreator) {
        runCatching {
            val methods = creator.obfsTable.filterKeys { it.startsWith("freeDataGuide_") }.values
                .ifEmpty {
                    locateBiliFreeDataGuides(creator.bridge).also { entries ->
                        entries.forEachIndexed { i, method -> creator.obfsTable["freeDataGuide_$i"] = method }
                        creator.changed = true
                    }
                }
            val unit = findClass("kotlin.Unit").getObjS("INSTANCE")!!
            methods.forEach { info ->
                // 在免流开通引导请求前结束，不拦截流量警告或已开通免流的状态提示。
                hooks += DexMethod(info.descriptor).getMethodInstance(classLoader).hookConstantIf(unit) { settings.isEnabled(BiliFeature.REMOVE_FREE_DATA_GUIDE) }
                logD("bili free data guide blocked: ${info.descriptor}")
            }
        }.onFailure { reportError(BiliFeature.REMOVE_FREE_DATA_GUIDE, "hookFreeDataGuide (Theseus)", it) }
        runCatching {
            // 旧 PlayerNetworkService 混合处理普通提醒和推广，空标题只跳过 TFToast 推广视图。
            hooks += findClass("com.bapis.bilibili.app.view.v1.TFToast")
                .hookConstantIf("getTitle", constant = "") { settings.isEnabled(BiliFeature.REMOVE_FREE_DATA_GUIDE) }
        }.onFailure { reportError(BiliFeature.REMOVE_FREE_DATA_GUIDE, "hookFreeDataGuide (legacy)", it) }
    }

    private fun filterActivityMeta(metas: List<*>): List<*> {
        val kept = metas.filterNot { raw ->
            raw is String && runCatching { JSONObject(raw).optInt("id") == 10011 }.getOrDefault(false)
        }
        return if (kept.size == metas.size) metas else kept
    }

    private fun hookLegacyCloudTvOverlay() {
        val replyClass = findClass("com.bapis.bilibili.community.service.dm.v1.DmViewReply")
        hooks += findClass("tv.danmaku.biliplayerv2.service.interact.biz.InteractLayerService")
            .hookBefore("setDmViewReply", replyClass) { param ->
                if (!settings.isEnabled(BiliFeature.REMOVE_CLOUD_TV_OVERLAY)) return@hookBefore
                runCatching {
                    val reply = param.args[0] ?: return@hookBefore
                    val metas = reply.call("getActivityMetaList") as? List<*>
                        ?: return@hookBefore
                    val kept = filterActivityMeta(metas)
                    if (kept === metas) return@hookBefore
                    // Chronos receives protobuf bytes, so replace the response before serialization.
                    val builder = reply.call("toBuilder") ?: return@hookBefore
                    builder.call("clearActivityMeta")
                    builder.call("addAllActivityMeta", kept)
                    param.args[0] = builder.call("build") ?: reply
                }.onFailure { reportError(BiliFeature.REMOVE_CLOUD_TV_OVERLAY, "filterCloudTvOverlay", it) }
            }
    }

    private fun hookSplashAd(creator: ObfsTableCreator) {
        hooks += Activity::class.java.hookAllBefore("startActivity") { param ->
            if (!settings.isEnabled(BiliFeature.REMOVE_SPLASH_AD)) return@hookAllBefore
            val intent = param.args.firstOrNull() as? Intent ?: return@hookAllBefore
            if (intent.component?.className?.contains("HotSplashActivity") == true
                || intent.data?.toString() == "bilibili://main/hot-splash2"
            ) {
                logD("bili hot splash ad removed")
                param.result = null
            }
        }
        runCatching {
            val m = creator.create("showSplashAd", ::locateBiliSplash)
            hooks += DexMethod(m.descriptor).getMethodInstance(classLoader).hookBefore { param ->
                if (!settings.isEnabled(BiliFeature.REMOVE_SPLASH_AD)) return@hookBefore
                param.args[1] = null
                logD("bili legacy splash ad removed")
            }
        }.onFailure { reportError(BiliFeature.REMOVE_SPLASH_AD, "hookSplashAd (legacy)", it) }
        if (findClassOrNull("kntr.srcs.app.splash.core.vm.BusinessSplashViewModel") != null) {
            runCatching {
                val m = creator.create("selectNewSplashAd", ::locateBiliNewSplash)
                // 本地 / 实时及冷 / 热开屏共用该选择器；原生 null 分支会发送结束事件并清理容器。
                hooks += DexMethod(m.descriptor).getMethodInstance(classLoader).hookConstantIf(null) { settings.isEnabled(BiliFeature.REMOVE_SPLASH_AD) }
                logD("bili new splash selector blocked: ${m.descriptor}")
            }.onFailure { reportError(BiliFeature.REMOVE_SPLASH_AD, "hookSplashAd (new)", it) }
        }
    }

    override fun onHook() {
        readSettings()
        addSettingsIntentInterceptor(onHookRegistered = { hooks += it }) { activity ->
            runCatching { BiliSettingsDialog(activity, this).show() }
                .onFailure { reportError(null, "打开设置", it) }
        }.onFailure { reportError(null, "安装设置入口", it) }
        ObfsTableCreator("bili", 2, appInfo = loadPackageParam.appInfo).use { creator ->
            BiliSettingsEntry(classLoader, { BiliSettingsDialog(it, this).show() },
                { stage, error -> reportError(null, stage, error) }).install(creator, hooks)
            hookShowAv(creator)
            hookShare(creator)
            hookNewShare(creator)
            hookForceNewShare(creator)
            hookCommentLink(creator)
            hookCommentLinkInVideo(creator)
            hookViewActions(creator)
            hookAd(creator)
            hookPauseAd(creator)
            hookFreeDataGuide(creator)
            hookCloudTvOverlay(creator)
            hookSplashAd(creator)
            creator.persist()
        }
    }

    override fun onHookError(t: Throwable) {
        reportError(null, "初始化", t)
    }

    override fun onUnhook(): Boolean {
        hooks.asReversed().forEach { it.unhook() }
        hooks.clear()
        return true
    }
}
