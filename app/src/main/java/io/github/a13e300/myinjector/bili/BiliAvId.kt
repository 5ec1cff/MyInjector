package io.github.a13e300.myinjector.bili

import io.github.a13e300.myinjector.arch.ObfsInfo
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Method
import java.lang.reflect.Modifier

internal fun biliAvSelector(type: Class<*>): Method = type.declaredMethods.single {
    Modifier.isStatic(it.modifiers) && it.returnType == String::class.java &&
        it.parameterTypes.contentEquals(arrayOf(String::class.java, String::class.java))
}

internal data class BiliVideoId(val aid: Long, val bvid: String)

/** 只改当前标题区的 ID 文案，正文里的 BV 链接仍由宿主识别。 */
internal class BiliAvHeadlineScope(private val videoId: (Any) -> BiliVideoId) {
    private val stack = ThreadLocal.withInitial { mutableListOf<BiliVideoId?>() }

    fun enter(receiver: Any, enabled: Boolean = true) {
        val frames = stack.get()!!
        frames.add(null)
        if (enabled) frames[frames.lastIndex] = videoId(receiver)
    }

    fun leave() {
        val frames = stack.get() ?: return
        if (frames.isNotEmpty()) frames.removeAt(frames.lastIndex)
        if (frames.isEmpty()) stack.remove()
    }

    fun text(original: Any?): Any? {
        val id = stack.get()?.lastOrNull() ?: return original
        return if (id.aid > 0 && id.bvid.isNotEmpty() && original == id.bvid) "AV${id.aid}" else original
    }
}

internal class BiliAvHeadline(private val loader: ClassLoader, private val members: Map<String, ObfsInfo>) {
    private fun method(key: String) = DexMethod(members.getValue(key).descriptor).getMethodInstance(loader)
    val render = method("render")
    val item = loader.loadClass(members.getValue("item").className).declaredConstructors.single()
    private val helper = DexField(members.getValue("helper").descriptor).getFieldInstance(loader)
        .apply { isAccessible = true }
    private val aid = method("aid")
    private val bvid = method("bvid")
    val scope = BiliAvHeadlineScope { receiver ->
        val current = helper.get(receiver)
        BiliVideoId(aid.invoke(current) as Long, bvid.invoke(current) as String)
    }
}
