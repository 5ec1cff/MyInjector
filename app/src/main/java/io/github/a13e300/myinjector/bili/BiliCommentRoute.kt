package io.github.a13e300.myinjector.bili

import android.net.Uri
import io.github.a13e300.myinjector.arch.ObfsInfo
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy
import java.net.URI

internal fun biliVideoCommentRouteUrl(url: String, rootCommentId: String?, secondaryCommentId: String?): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    if (uri.scheme !in listOf("http", "https") || uri.host != "www.bilibili.com" ||
        uri.rawUserInfo != null || uri.port != -1 ||
        !Regex("/video/(av[1-9][0-9]*|BV[0-9A-Za-z]{10})/?").matches(uri.rawPath ?: "")) return null
    val root = rootCommentId?.toLongOrNull()?.takeIf { it > 0 } ?: return null
    val target = if (secondaryCommentId == null) root
        else secondaryCommentId.toLongOrNull()?.takeIf { it > 0 } ?: return null
    if (uri.fragment != "reply$target") return null
    // 查询参数已能定位评论，只在 App 路由副本中去掉触发独立评论页的 fragment。
    return url.substringBefore('#')
}

internal class BiliCommentRoute(classLoader: ClassLoader, members: Map<String, ObfsInfo>) {
    val legacy = DexMethod(members.getValue("legacy").descriptor).getMethodInstance(classLoader)
    val theseus2 = DexMethod(members.getValue("theseus2").descriptor).getMethodInstance(classLoader)
    private val chainType = legacy.parameterTypes.single()
    private val getRequest = chainType.getMethod("getRequest")
    private val requestType = getRequest.returnType
    private val getTargetUri = requestType.getMethod("getTargetUri")
    private val getUniformUrl = requestType.getMethod("getUniformUrl")
    private val newBuilder = requestType.getMethod("newBuilder")
    private val builderType = newBuilder.returnType
    private val setTargetUri = builderType.getMethod("setTargetUri", Uri::class.java)
    private val build = builderType.getMethod("build")
    private val contextType = theseus2.parameterTypes.first()
    private val getUri = contextType.getMethod("getUri")
    private val getUriParam = contextType.getMethod("getUriParam", String::class.java)
    private val uriCompanion = getUri.returnType.getField("Companion").get(null)!!
    private val parseUri = uriCompanion.javaClass.getMethod("parse", String::class.java)

    fun wrapLegacy(chain: Any): Any {
        val request = getRequest.invoke(chain)!!
        val fullUri = getUniformUrl.invoke(request) as Uri
        if (biliVideoCommentRouteUrl(fullUri.toString(), fullUri.getQueryParameter("comment_root_id"),
                fullUri.getQueryParameter("comment_secondary_id")) == null) return chain
        // newBuilder 保留 extras、flags 等数据，不修改原始请求及剪贴板中的 URL。
        val builder = newBuilder.invoke(request)
        val targetUri = getTargetUri.invoke(request) as Uri
        setTargetUri.invoke(builder, targetUri.buildUpon().fragment(null).build())
        return withGetter(chainType, chain, "getRequest", build.invoke(builder))
    }

    fun wrapTheseus2(context: Any): Any {
        // 宿主可能已将 query 拆入请求参数，不能只从 getUri() 的字符串读取评论 ID。
        val url = biliVideoCommentRouteUrl(getUri.invoke(context)!!.toString(),
            getUriParam.invoke(context, "comment_root_id") as? String,
            getUriParam.invoke(context, "comment_secondary_id") as? String) ?: return context
        return withGetter(contextType, context, "getUri", parseUri.invoke(uriCompanion, url))
    }

    private fun withGetter(type: Class<*>, source: Any, getter: String, value: Any?): Any =
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            if (method.name == getter && method.parameterCount == 0) value
            else try {
                method.invoke(source, *(args ?: emptyArray()))
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
}
