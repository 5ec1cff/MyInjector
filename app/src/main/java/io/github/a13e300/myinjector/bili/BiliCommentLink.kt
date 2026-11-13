package io.github.a13e300.myinjector.bili

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.currentApplication
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Proxy

internal fun biliCommentUrl(oid: Long, type: Int, commentId: Long, rootCommentId: Long): String? {
    if (oid <= 0 || commentId <= 0 || rootCommentId < 0) return null
    val base = when (type) {
        1 -> "https://www.bilibili.com/video/av$oid"
        11 -> "https://t.bilibili.com/$oid"
        17 -> "https://t.bilibili.com/$oid"
        else -> return null
    }
    val root = rootCommentId.takeIf { it > 0 } ?: commentId
    return buildString {
        append(base).append("?comment_on=1&comment_root_id=").append(root)
        if (root != commentId) append("&comment_secondary_id=").append(commentId)
        // 图文评论的 oid 是图片动态 ID，需要 type=2 才能由路由找到对应动态。
        if (type == 11) append("&type=2")
        append("#reply").append(commentId)
    }
}

internal class BiliCommentLink(
    private val classLoader: ClassLoader,
    private val members: Map<String, ObfsInfo>,
) {
    private fun field(key: String) = DexField(members.getValue(key).descriptor)
        .getFieldInstance(classLoader).apply { isAccessible = true }
    private fun method(key: String) = DexMethod(members.getValue(key).descriptor)
        .getMethodInstance(classLoader).apply { isAccessible = true }

    val menu = method("menu")
    val renderers = listOf("panel", "sheet", "bubble").map(::method)
    private val commentId = field("commentId")
    private val rootCommentId = field("rootCommentId")
    private val oid = field("oid")
    private val type = field("type")
    private val title = field("entryTitle")
    private val icon = field("entryIcon")
    private val function0 = classLoader.loadClass("kotlin.jvm.functions.Function0")
    private val invoke = function0.getMethod("invoke")
    private val unit = classLoader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
    private val constructor = title.declaringClass.getDeclaredConstructor(
        String::class.java, icon.type, String::class.java, icon.type, function0
    ).apply { isAccessible = true }
    private data class Menu(val url: String, val dismiss: Any)
    private val menus = ThreadLocal.withInitial { mutableListOf<Menu?>() }

    fun enter(args: Array<Any?>) {
        // Compose 子菜单也可能单独重组，仅在评论菜单调用范围内插入新项。
        val stack = menus.get()!!
        stack.add(null)
        val model = args[0]!!
        val url = biliCommentUrl(
            (oid.get(model) as? Long) ?: 0,
            (type.get(model) as? Int) ?: 0,
            commentId.getLong(model), rootCommentId.getLong(model)
        )
        stack[stack.lastIndex] = url?.let { Menu(it, args[2]!!) }
    }

    fun leave() {
        val stack = menus.get() ?: return
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) menus.remove()
    }

    fun addEntry(args: Array<Any?>, listIndex: Int) {
        val menu = menus.get()?.lastOrNull() ?: return
        val groups = args[listIndex] as List<*>
        val firstGroup = groups.firstOrNull() as? List<*> ?: return
        // 当前原生菜单第一组的最后一项固定为“复制文本”，沿用它的图标。
        val copyText = firstGroup.lastOrNull() ?: return
        if (groups.any { group -> (group as List<*>).any { title.get(it) == COPY_LINK } }) return
        val action = Proxy.newProxyInstance(function0.classLoader, arrayOf(function0)) { proxy, method, values ->
            when (method.name) {
                "invoke" -> {
                    val context = currentApplication()
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText(COPY_LINK, menu.url))
                    invoke.invoke(menu.dismiss)
                    Toast.makeText(context, "已复制评论链接", Toast.LENGTH_SHORT).show()
                    unit
                }
                "toString" -> COPY_LINK
                "hashCode" -> System.identityHashCode(proxy)
                "equals" -> proxy === values?.firstOrNull()
                else -> error("Unexpected callback: $method")
            }
        }
        val entry = constructor.newInstance(COPY_LINK, icon.get(copyText), null, null, action)
        args[listIndex] = listOf(firstGroup + entry) + groups.drop(1)
    }

    companion object {
        private const val COPY_LINK = "复制评论链接"
    }
}
