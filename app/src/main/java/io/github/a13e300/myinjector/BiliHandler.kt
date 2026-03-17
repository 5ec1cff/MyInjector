package io.github.a13e300.myinjector

import android.net.Uri
import android.os.Bundle
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.createObfsTable
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookAllNop
import io.github.a13e300.myinjector.arch.toObfsInfo

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

    private fun hookAd() {
        runCatching {
            // 视频底部广告
            val tbl = createObfsTable("bili", 1) { bridge ->
                val method =
                    bridge.findMethod {
                        matcher {
                            usingEqStrings("DetailAdService", "-", "onCreateViews")
                        }
                    }.single().toObfsInfo()

                mutableMapOf("DetailAdService-onCreateViews" to method)
            }
            val adCreateView = tbl["DetailAdService-onCreateViews"]!!
            findClass(adCreateView.className).hookAllNop(adCreateView.memberName)
        }.onFailure {
            logE("hookAd", it)
        }
    }

    override fun onHook() {
        hookShare()
        hookAd()
    }
}
