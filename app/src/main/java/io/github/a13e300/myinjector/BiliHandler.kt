package io.github.a13e300.myinjector

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.call
import io.github.a13e300.myinjector.arch.getObjS
import io.github.a13e300.myinjector.arch.hookAllCAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookConstant
import io.github.a13e300.myinjector.bili.locateBiliActivityMetaField
import io.github.a13e300.myinjector.bili.locateBiliFreeDataGuides
import io.github.a13e300.myinjector.bili.locateBiliNewSplash
import io.github.a13e300.myinjector.bili.locateBiliPausePageConfig
import io.github.a13e300.myinjector.bili.locateBiliShare
import io.github.a13e300.myinjector.bili.locateBiliSplash
import io.github.a13e300.myinjector.bili.locateBiliUnderPlayerAds
import io.github.a13e300.myinjector.bridge.Unhook
import org.json.JSONObject
import org.luckypray.dexkit.wrap.DexMethod

class BiliHandler : IHook() {
    private val hooks = mutableListOf<Unhook>()

    private fun hookShare(creator: ObfsTableCreator) {
        val b23TvRegex = Regex("https?://b23\\.tv/[0-9a-zA-Z]+")
        val filterOutParams = listOf("share_session_id", "share_source", "bbid", "share_medium")
        runCatching {
            // 单参数 shareTo 会在 delegate 内生成 Bundle；两条分享路径最终都到达此方法。
            val info = creator.create("shareWithContent", ::locateBiliShare)
            hooks += DexMethod(info.descriptor).getMethodInstance(classLoader).hookBefore { param ->
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
            }
            logD("bili share content filter installed: ${info.descriptor}")
        }.onFailure {
            logE("hookShare", it)
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
                hooks += method.hookConstant(result)
                logD("bili under-player ad renderer blocked: ${info.descriptor}")
            }
        }.onFailure {
            logE("hookAd", it)
        }
    }

    private fun hookPauseAd(creator: ObfsTableCreator) {
        // Disable the pause-ad flow before it requests an ad or starts the countdown.
        runCatching {
            hooks += findClass("com.bilibili.adcommon.config.DDConfig")
                .hookConstant("getPausePageEnable", constant = false)
        }.onFailure { logE("hookPauseAd (Theseus)", it) }
        if (findClassOrNull("com.bilibili.ship.theseus2.united.page.pausedpage.PausedPageController") != null) {
            runCatching {
                val info = creator.create("pausePageConfig", ::locateBiliPausePageConfig)
                hooks += DexMethod(info.descriptor).getMethodInstance(classLoader).hookConstant(false)
                logD("bili pause ad config blocked: ${info.descriptor}")
            }.onFailure { logE("hookPauseAd (Theseus2)", it) }
        }
    }

    private fun hookCloudTvOverlay(creator: ObfsTableCreator) {
        runCatching { hookLegacyCloudTvOverlay() }.onFailure { logE("hookCloudTvOverlay (Theseus)", it) }
        val kotlinReply = findClassOrNull("com.bapis.bilibili.community.service.dm.v1.KDmViewReply") ?: return
        runCatching {
            val info = creator.create("activityMetaField", ::locateBiliActivityMetaField)
            val field = kotlinReply.getDeclaredField(info.memberName).apply { isAccessible = true }
            hooks += kotlinReply.hookAllCAfter { param ->
                if (param.throwable != null) return@hookAllCAfter
                runCatching {
                    val metas = field.get(param.thisObject) as? List<*> ?: return@hookAllCAfter
                    val kept = filterActivityMeta(metas)
                    if (kept !== metas) field.set(param.thisObject, kept)
                }.onFailure { logE("filterCloudTvOverlay (Theseus2)", it) }
            }
            logD("bili Kotlin activity metadata filtered: ${info.descriptor}")
        }.onFailure { logE("hookCloudTvOverlay (Theseus2)", it) }
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
                hooks += DexMethod(info.descriptor).getMethodInstance(classLoader).hookConstant(unit)
                logD("bili free data guide blocked: ${info.descriptor}")
            }
        }.onFailure { logE("hookFreeDataGuide (Theseus)", it) }
        runCatching {
            // 旧 PlayerNetworkService 混合处理普通提醒和推广，空标题只跳过 TFToast 推广视图。
            hooks += findClass("com.bapis.bilibili.app.view.v1.TFToast")
                .hookConstant("getTitle", constant = "")
        }.onFailure { logE("hookFreeDataGuide (legacy)", it) }
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
                }.onFailure { logE("filterCloudTvOverlay", it) }
            }
    }

    private fun hookSplashAd(creator: ObfsTableCreator) {
        hooks += Activity::class.java.hookAllBefore("startActivity") { param ->
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
                param.args[1] = null
                logD("bili legacy splash ad removed")
            }
        }.onFailure { logE("hookSplashAd (legacy)", it) }
        if (findClassOrNull("kntr.srcs.app.splash.core.vm.BusinessSplashViewModel") != null) {
            runCatching {
                val m = creator.create("selectNewSplashAd", ::locateBiliNewSplash)
                // 本地 / 实时及冷 / 热开屏共用该选择器；原生 null 分支会发送结束事件并清理容器。
                hooks += DexMethod(m.descriptor).getMethodInstance(classLoader).hookConstant(null)
                logD("bili new splash selector blocked: ${m.descriptor}")
            }.onFailure { logE("hookSplashAd (new)", it) }
        }
    }

    override fun onHook() {
        ObfsTableCreator("bili", 2, appInfo = loadPackageParam.appInfo).use { creator ->
            hookShare(creator)
            hookAd(creator)
            hookPauseAd(creator)
            hookFreeDataGuide(creator)
            hookCloudTvOverlay(creator)
            hookSplashAd(creator)
            creator.persist()
        }
    }

    override fun onUnhook(): Boolean {
        hooks.asReversed().forEach { it.unhook() }
        hooks.clear()
        return true
    }
}
