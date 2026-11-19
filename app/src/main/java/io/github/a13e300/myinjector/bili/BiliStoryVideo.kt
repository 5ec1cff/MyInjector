package io.github.a13e300.myinjector.bili

import android.content.Context
import android.net.Uri
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.bridge.Unhook
import java.net.URI
import java.net.URLDecoder

/** 只处理有明确稿件的 Story 地址，不把随机推荐入口当成 av0。 */
internal fun biliStoryVideoUrl(url: String, newRouter: Boolean): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme !in listOf("bilibili", "experimental-bilibili") || uri.rawAuthority != "story") return null
    val id = uri.path?.removePrefix("/") ?: return null
    val aid = id.removePrefix("av").takeIf { Regex("[1-9][0-9]*").matches(it) }?.toLongOrNull()
    if (aid == null) {
        if (!Regex("BV[0-9A-Za-z]{10}").matches(id)) return null
    }
    // 保留原始编码、重复参数和评论 fragment，只去掉强制选择 Story 的参数。
    val query = uri.rawQuery?.split('&')?.filterNot { part ->
        val name = URLDecoder.decode(part.substringBefore('='), "UTF-8")
        val value = URLDecoder.decode(part.substringAfter('=', ""), "UTF-8")
        name in listOf("-Arouter", "-Atype") && value == "story"
    }?.joinToString("&")?.takeIf { it.isNotEmpty() }
    val scheme = if (newRouter) "experimental-bilibili" else uri.scheme
    return "$scheme://video${uri.rawPath}" + (query?.let { "?$it" } ?: "") +
        (uri.rawFragment?.let { "#$it" } ?: "")
}

internal class BiliStoryVideo(
    private val loader: ClassLoader,
    private val isEnabled: () -> Boolean,
    private val onError: (String, Throwable) -> Unit,
) {
    fun install(hooks: MutableList<Unhook>) {
        runCatching { installLegacy(hooks) }.onFailure { onError("安装旧版 Story 路由", it) }
        runCatching { installNew(hooks) }.onFailure { onError("安装新版 Story 路由", it) }
        runCatching { disableAutoStory(hooks) }.onFailure { onError("关闭自动进入 Story", it) }
    }

    private fun installLegacy(hooks: MutableList<Unhook>) {
        val request = loader.loadClass("com.bilibili.lib.blrouter.RouteRequest")
        val getUri = request.getMethod("getTargetUri")
        val newBuilder = request.getMethod("newBuilder")
        val builder = newBuilder.returnType
        val setUri = builder.getMethod("setTargetUri", Uri::class.java)
        val getExtras = builder.getMethod("getExtras")
        val getExtra = loader.loadClass("com.bilibili.lib.blrouter.BundleLike")
            .getMethod("get", String::class.java)
        val removeExtra = loader.loadClass("com.bilibili.lib.blrouter.MutableBundleLike")
            .getMethod("remove", String::class.java)
        val build = builder.getMethod("build")
        val newCall = loader.loadClass("com.bilibili.lib.blrouter.BLRouter").getMethod("newCall",
            request, Context::class.java, loader.loadClass("androidx.fragment.app.Fragment"),
            loader.loadClass("com.bilibili.lib.blrouter.RequestMode"), Boolean::class.javaPrimitiveType)
        hooks += newCall.hookBefore(cond = isEnabled) { param ->
            runCatching {
                val original = param.args[0]
                val url = biliStoryVideoUrl((getUri.invoke(original) as Uri).toString(), false) ?: return@runCatching
                // 复制请求，保留 flags、extras、回调和动画，让宿主重新匹配普通视频页。
                val copy = newBuilder.invoke(original)
                setUri.invoke(copy, Uri.parse(url))
                val extras = getExtras.invoke(copy)
                listOf("-Arouter", "-Atype").forEach { key ->
                    if (getExtra.invoke(extras, key) == "story") removeExtra.invoke(extras, key)
                }
                param.args[0] = build.invoke(copy)
            }.onFailure { onError("替换旧版 Story 跳转", it) }
        }
    }

    private fun installNew(hooks: MutableList<Unhook>) {
        val uri = loader.loadClass("com.bilibili.lib.brouter.uri.Uri")
        val companion = uri.getField("Companion").get(null)!!
        val parse = companion.javaClass.getMethod("parse", String::class.java)
        val router = loader.loadClass("kntr.base.router.Router")
        listOf("find", "launch").forEach { name ->
            hooks += router.getMethod(name, uri).hookBefore(cond = isEnabled) { param ->
                runCatching {
                    val url = biliStoryVideoUrl(param.args[0].toString(), true) ?: return@runCatching
                    // 新路由使用 experimental scheme 进入视频参数转换，再创建普通详情页。
                    param.args[0] = parse.invoke(companion, url)
                }.onFailure { onError("替换新版 Story 跳转 ($name)", it) }
            }
        }
    }

    private fun disableAutoStory(hooks: MutableList<Unhook>) {
        // 9.19 的实际读取方已改用 Kotlin 配置，旧 protobuf getter 只剩 Builder 调用。
        val disabled = loader.loadClass("com.bapis.bilibili.app.distribution.KBoolValue")
            .getConstructor().newInstance()
        val config = loader.loadClass("com.bapis.bilibili.app.distribution.setting.play.KPlayConfig")
        listOf("getLandscapeAutoStory", "getShouldAutoStory").forEach { name ->
            hooks += config.getMethod(name).hookBefore(cond = isEnabled) { it.result = disabled }
        }
    }
}
