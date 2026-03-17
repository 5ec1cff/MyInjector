package io.github.a13e300.myinjector

import android.net.Uri
import android.os.Bundle
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.MutableObfsTable
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllNop
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge

class BiliHandler : IHook() {
    private fun hookShare() {
        val b23TvRegex = Regex("https?://b23\\.tv/[0-9a-zA-Z]+")
        val filterOutParams = listOf("share_session_id", "share_source", "bbid", "share_medium")
        runCatching {
            findClass("com.bilibili.lib.sharewrapper.ShareHelperV2").hookAllBefore("shareTo") { param ->
                val b = param.args[1] as Bundle
                val content = b.getString("params_content") ?: return@hookAllBefore
                val url = b.getString("params_target_url") ?: return@hookAllBefore
                val res = b23TvRegex.find(content) ?: return@hookAllBefore
                val sanitizedUrl = Uri.parse(url).run {
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
        }.onFailure {
            logE("hookShare", it)
        }
    }

    private fun locateAd(bridge: DexKitBridge, obfsTable: MutableObfsTable) = runCatching {
        val method =
            bridge.findMethod {
                matcher {
                    usingEqStrings("DetailAdService", "-", "onCreateViews")
                }
            }.single().toObfsInfo()

        obfsTable["DetailAdService-onCreateViews"] = method
    }.onFailure {
        logE("locateAd", it)
    }

    private fun hookAd(tbl: ObfsTable) {
        runCatching {
            // 视频底部广告
            val adCreateView = tbl["DetailAdService-onCreateViews"]!!
            findClass(adCreateView.className).hookAllNop(adCreateView.memberName)
        }.onFailure {
            logE("hookAd", it)
        }
    }

    private fun locateSplashAd(bridge: DexKitBridge, obfsTable: MutableObfsTable) = runCatching {
        val method = bridge.findMethod {
            matcher {
                usingEqStrings("show splash id = ", "ADSplashFragment")
            }
        }.single().toObfsInfo()

        obfsTable["showSplashAd"] = method
    }.onFailure {
        logE("locateSplashAd", it)
    }

    private fun hookSplashAd(obfsTable: ObfsTable) = runCatching {
        val m = obfsTable["showSplashAd"]!!
        findClass(m.className).hookAllBefore(m.memberName) { param ->
            param.args[1] = null
            logD("fuck splash ad")
        }
    }.onFailure {
        logE("hookSplashAd", it)
    }

    override fun onHook() {
        hookShare()
        val tbl = createObfsTable("bili", 1) { bridge ->
            val res = mutableMapOf<String, ObfsInfo>()

            locateAd(bridge, res)
            locateSplashAd(bridge, res)

            res
        }
        hookAd(tbl)
        hookSplashAd(tbl)
    }
}
