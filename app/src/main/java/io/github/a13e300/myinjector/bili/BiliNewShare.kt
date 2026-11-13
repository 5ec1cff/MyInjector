package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier
import java.net.URI

private val videoPath = Regex("/video/(BV[0-9A-Za-z]{10}|av[1-9][0-9]*)/?")
private val dynamicPath = Regex("/[1-9][0-9]*/?")
private val opusPath = Regex("/opus/[1-9][0-9]*/?")
private val shareTrackingParams = setOf("share_session_id", "share_source", "bbid", "share_medium")

internal fun localBiliShareUrl(text: String?): String? {
    val uri = text?.let { runCatching { URI(it) }.getOrNull() } ?: return null
    if (uri.scheme !in listOf("http", "https") || uri.rawUserInfo != null || uri.port != -1) return null
    val path = uri.rawPath ?: return null
    val supported = when (uri.host) {
        "www.bilibili.com" -> videoPath.matches(path) || opusPath.matches(path)
        "t.bilibili.com" -> dynamicPath.matches(path)
        else -> false
    }
    if (!supported) return null
    val query = uri.rawQuery?.split('&')?.filterNot {
        it.substringBefore('=') in shareTrackingParams
    }?.joinToString("&")?.takeIf { it.isNotEmpty() }
    return buildString {
        append(uri.scheme).append("://").append(uri.rawAuthority).append(path)
        if (query != null) append('?').append(query)
        if (uri.rawFragment != null) append('#').append(uri.rawFragment)
    }
}

internal fun formatBiliShareText(title: String?, content: String?, url: String): String? {
    val link = localBiliShareUrl(url) ?: return null
    // 视频 content 是作者和分 P 描述，图文 content 才是分享模型里的正文。
    val body = content.takeUnless { videoPath.matches(URI(link).rawPath) }
    val text = listOf(title, body).mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
        .distinct().filterNot { it == link }
    return (text + link).joinToString("\n")
}

internal fun localBiliCopyText(text: String?): String? {
    if (text == null) return null
    val urlStart = text.lastIndexOf('\n') + 1
    val url = localBiliShareUrl(text.substring(urlStart)) ?: return null
    return text.substring(0, urlStart) + url
}

internal class BiliNewShare(private val classLoader: ClassLoader, private val members: Map<String, ObfsInfo>) {
    private fun method(key: String) = DexMethod(members.getValue(key).descriptor)
        .getMethodInstance(classLoader).apply { isAccessible = true }
    private fun field(key: String) = DexField(members.getValue(key).descriptor)
        .getFieldInstance(classLoader).apply { isAccessible = true }

    val engine = method("engine")
    val contentProvider = method("contentProvider")
    private val providerField = field("providerField")
    private val findFactory = method("findFactory")
    private val baseInfoTitle = field("baseInfoTitle")
    private val baseInfoContent = field("baseInfoContent")
    private val baseInfoLink = field("baseInfoLink")
    private val copyText = field("copyText")
    private val copyClass = copyText.declaringClass
    private val copyConstructor = copyClass.getDeclaredConstructor(String::class.java)
        .apply { isAccessible = true }
    private val targetName = engine.parameterTypes[0].declaredMethods.single {
        !Modifier.isStatic(it.modifiers) && it.parameterCount == 0 && it.returnType == String::class.java
    }.apply { isAccessible = true }

    fun localContent(config: Any, target: Any, content: Any?): Any? {
        if (!copyClass.isInstance(content) || targetName.invoke(target) != "COPY") return content
        val factory = findFactory.invoke(providerField.get(config), target) ?: return content
        // R8 可能把闭包字段合并成 Object，按实际捕获的 ShareBaseInfo 读取链接。
        val baseInfo = factory.javaClass.declaredFields.filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.apply { isAccessible = true }.get(factory) }
            .singleOrNull { baseInfoLink.declaringClass.isInstance(it) } ?: return content
        val url = baseInfoLink.get(baseInfo) as? String ?: return content
        val text = formatBiliShareText(baseInfoTitle.get(baseInfo) as? String,
            baseInfoContent.get(baseInfo) as? String, url) ?: return content
        return copyConstructor.newInstance(text)
    }

    fun useLocalCopy(args: Array<Any?>): Boolean {
        val content = args[1]
        if (!copyClass.isInstance(content) || targetName.invoke(args[0]) != "COPY") return false
        val text = localBiliCopyText(copyText.get(content) as? String) ?: return false
        args[1] = copyConstructor.newInstance(text)
        // 原生 disableFetch 分支保留复制 Handler、成功回调和面板收尾。
        args[2] = true
        return true
    }
}
