package io.github.a13e300.myinjector.bili

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.app.Activity
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookAllCBefore
import io.github.a13e300.myinjector.logI
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy
import java.util.Collections
import java.util.WeakHashMap

/** 工具栏的第二行操作独立于分享渠道列表。 */
internal class BiliSubtitleToolbar(
    private val loader: ClassLoader,
    private val members: Map<String, ObfsInfo>,
    private val subtitles: BiliSubtitles,
) {
    private fun method(key: String) = DexMethod(members.getValue(key).descriptor).getMethodInstance(loader)
    private val group = method("toolbarGroup")
    private val groupOwner = DexField(members.getValue("toolbarGroupOwner").descriptor).getFieldInstance(loader)
        .apply { isAccessible = true }
    private val render = method("toolbarItem")
    private val itemType = render.parameterTypes[0]
    private val itemConstructor = itemType.declaredConstructors.single { it.parameterCount == 7 }
    private val clickType = itemConstructor.parameterTypes[4]
    private val click = clickType.getMethod("invoke")
    private val clickable = method("toolbarClickable")
    private val row = loader.loadClass("com.bilibili.playerbizcommonv2.widget.setting.channel.VideoSettingButtonComponent")
    private val function0 = loader.loadClass("kotlin.jvm.functions.Function0")
    private val function1 = loader.loadClass("kotlin.jvm.functions.Function1")
    private val unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
    private val noAction = Proxy.newProxyInstance(loader, arrayOf(function0, function1)) { proxy, method, args ->
        when (method.name) {
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.get(0)
            "toString" -> "SubtitleMenuAction"
            else -> unit
        }
    }
    private val stateFlow = loader.loadClass("kotlinx.coroutines.flow.StateFlowKt").getMethod("MutableStateFlow", Any::class.java)
    private val alpha = loader.loadClass("androidx.compose.ui.draw.AlphaKt")
        .getMethod("alpha", clickable.returnType, Float::class.javaPrimitiveType)
    private data class Action(val toolbar: WeakReference<Any>, val menu: BiliSubtitles.Menu)
    private data class Group(val toolbar: Any, var added: Boolean = false)
    private val scope = ThreadLocal<Group>()
    private val disabled = ThreadLocal<Boolean>()
    private val actions = Collections.synchronizedMap(WeakHashMap<Any, Action>())
    private val items = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private var iconPath: String? = null

    @Synchronized
    private fun icon(activity: Activity): String {
        iconPath?.let { return it }
        val file = activity.cacheDir.resolve("myinjector_subtitle_icon.png")
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        Canvas(bitmap).drawText("CC", 24f, 33f, paint)
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        // 宿主图片加载器接受本地路径，不支持 data URL。
        return file.absolutePath.also { iconPath = it }
    }

    fun install() = buildList {
        add(group.hook(before = { scope.set(Group(groupOwner.get(it.thisObject)!!)) }, after = { scope.remove() }))
        addAll(row.hookAllCBefore { param ->
            val current = scope.get() ?: return@hookAllCBefore
            if (current.added) return@hookAllCBefore
            val menu = subtitles.toolbarSnapshot(current.toolbar)
            // 宿主把点击字段收窄为具体 lambda 类，独立实例用作标记，在 invoke 前处理。
            val action = clickType.declaredConstructors.single().newInstance(*arrayOfNulls<Any>(6))
            val badge = itemConstructor.parameterTypes[2].declaredConstructors.single().let { constructor ->
                constructor.newInstance(*constructor.parameterTypes.map { if (it == String::class.java) "" else 0 }.toTypedArray<Any>())
            }
            val expose = itemConstructor.parameterTypes[6].getConstructor(function0).newInstance(noAction)
            val item = itemConstructor.newInstance(icon(menu.activity.get()!!), false, badge, stateFlow.invoke(null, menu.label), action, noAction, expose)
            actions[action] = Action(WeakReference(current.toolbar), menu)
            items[item] = menu.tracks.isEmpty()
            param.args[0] = listOf(item) + (param.args[0] as List<*>)
            current.added = true
            logI("bili subtitle toolbar inserted: tracks=${menu.tracks.size}, label=${menu.label}")
        })
        add(click.hookBefore { param ->
            val action = actions[param.thisObject] ?: return@hookBefore
            val activity = action.menu.activity.get()
            if (action.menu.tracks.isNotEmpty() && activity != null) {
                val toolbar = action.toolbar.get()
                val close = toolbar?.javaClass?.declaredFields?.single { it.type == function0 }
                    ?.apply { isAccessible = true }?.get(toolbar)
                if (close != null) function0.getMethod("invoke").invoke(close)
                activity.window.decorView.post { showBiliSubtitleDialog(activity, action.menu.tracks) }
            }
            param.result = unit
        })
        add(render.hook(before = { disabled.set(items[it.args[0]] == true) }, after = { disabled.remove() }))
        add(clickable.hook(before = { param ->
            if (disabled.get() == true) {
                param.args[1] = false
                param.args[3] = (param.args[3] as Int) and 1.inv()
            }
        }, after = { param ->
            if (disabled.get() == true && param.throwable == null) param.result = alpha.invoke(null, param.result, 0.38f)
        }))
    }
}
