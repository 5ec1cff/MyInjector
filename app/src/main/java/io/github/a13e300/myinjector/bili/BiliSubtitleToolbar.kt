package io.github.a13e300.myinjector.bili

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.app.Activity
import android.content.Context
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.findBaseActivity
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookAllCBefore
import io.github.a13e300.myinjector.logI
import io.github.a13e300.myinjector.bridge.Unhook
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
    private val subtitles: BiliSubtitles?,
    private val cover: BiliCover?,
    private val isEnabled: (BiliFeature) -> Boolean,
    private val onError: (BiliFeature, String, Throwable) -> Unit,
) {
    private fun method(key: String) = DexMethod(members.getValue(key).descriptor).getMethodInstance(loader)
    private val group = method("toolbarGroup")
    private val groupOwner = DexField(members.getValue("toolbarGroupOwner").descriptor).getFieldInstance(loader)
        .apply { isAccessible = true }
    private val toolbarContext = DexField(members.getValue("toolbarContext").descriptor).getFieldInstance(loader)
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
    private data class Action(val toolbar: WeakReference<Any>, val menu: BiliSubtitles.Menu, val enabled: Boolean, val feature: BiliFeature, val coverUrl: String? = null)
    private data class Group(val toolbar: Any, var added: Boolean = false)
    private val scope = ThreadLocal<Group>()
    private val disabled = ThreadLocal<Boolean>()
    private val actions = Collections.synchronizedMap(WeakHashMap<Any, Action>())
    private val items = Collections.synchronizedMap(WeakHashMap<Any, Boolean>())
    private val iconPaths = mutableMapOf<BiliFeature, String>()

    @Synchronized
    private fun icon(activity: Activity, feature: BiliFeature): String {
        if (feature == BiliFeature.OPEN_VIDEO_IN_NEW_TASK) return biliVideoTaskIcon(activity)
        iconPaths[feature]?.let { return it }
        val file = activity.cacheDir.resolve("myinjector_${feature.key}_icon.png")
        val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 26f
            textAlign = Paint.Align.CENTER
        }
        val canvas = Canvas(bitmap)
        if (feature == BiliFeature.VIEW_COVER) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 3f
            canvas.drawRoundRect(6f, 8f, 42f, 40f, 3f, 3f, paint)
            canvas.drawLine(7f, 32f, 20f, 20f, paint)
            canvas.drawLine(20f, 20f, 40f, 38f, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(33f, 17f, 4f, paint)
        } else {
            canvas.drawText("CC", 24f, 33f, paint)
        }
        try {
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally {
            bitmap.recycle()
        }
        // 宿主图片加载器接受本地路径，不支持 data URL。
        return file.absolutePath.also { iconPaths[feature] = it }
    }

    private fun item(toolbar: Any, menu: BiliSubtitles.Menu, label: String, enabled: Boolean, feature: BiliFeature = BiliFeature.VIEW_SUBTITLES, coverUrl: String? = null): Any {
        // 宿主把点击字段收窄为具体 lambda 类，独立实例用作标记，在 invoke 前处理。
        val action = clickType.declaredConstructors.single().newInstance(*arrayOfNulls<Any>(6))
        val badge = itemConstructor.parameterTypes[2].declaredConstructors.single().let { constructor ->
            constructor.newInstance(*constructor.parameterTypes.map { if (it == String::class.java) "" else 0 }.toTypedArray<Any>())
        }
        val expose = itemConstructor.parameterTypes[6].getConstructor(function0).newInstance(noAction)
        val item = itemConstructor.newInstance(icon(menu.activity.get()!!, feature), false,
            badge, stateFlow.invoke(null, label), action, noAction, expose)
        actions[action] = Action(WeakReference(toolbar), menu, enabled, feature, coverUrl)
        items[item] = !enabled
        return item
    }

    private fun <T> menuAction(stage: String, action: () -> T): T? = runCatching(action).onFailure { error ->
        if (subtitles != null) onError(BiliFeature.VIEW_SUBTITLES, stage, error)
        if (cover != null) onError(BiliFeature.VIEW_COVER, stage, error)
        onError(BiliFeature.OPEN_VIDEO_IN_NEW_TASK, stage, error)
    }.getOrNull()

    fun install(hooks: MutableList<Unhook>) {
        hooks += group.hook(before = { param ->
            scope.remove()
            menuAction("读取视频操作菜单") { scope.set(Group(groupOwner.get(param.thisObject)!!)) }
        }, after = { scope.remove() })
        hooks += row.hookAllCBefore { param ->
            val current = scope.get() ?: return@hookAllCBefore
            if (current.added) return@hookAllCBefore
            current.added = true
            val added = buildList {
                if (subtitles != null && isEnabled(BiliFeature.VIEW_SUBTITLES)) runCatching {
                    val menu = subtitles.toolbarSnapshot(current.toolbar)
                    add(item(current.toolbar, menu, menu.label, menu.tracks.isNotEmpty()))
                    logI("bili subtitle toolbar inserted: tracks=${menu.tracks.size}, label=${menu.label}")
                }.onFailure { onError(BiliFeature.VIEW_SUBTITLES, "插入字幕按钮", it) }
                if (cover != null && isEnabled(BiliFeature.VIEW_COVER)) runCatching {
                    val activity = (toolbarContext.get(current.toolbar) as Context).findBaseActivity()
                    val menu = BiliSubtitles.Menu(WeakReference(activity), emptyList(), "")
                    val url = cover.snapshot(current.toolbar)
                    add(item(current.toolbar, menu, "查看封面", url != null, BiliFeature.VIEW_COVER, url))
                }.onFailure { onError(BiliFeature.VIEW_COVER, "插入封面按钮", it) }
                if (isEnabled(BiliFeature.OPEN_VIDEO_IN_NEW_TASK)) runCatching {
                    val activity = (toolbarContext.get(current.toolbar) as Context).findBaseActivity()
                    val menu = BiliSubtitles.Menu(WeakReference(activity), emptyList(), "")
                    add(item(current.toolbar, menu, "在新任务打开", true, BiliFeature.OPEN_VIDEO_IN_NEW_TASK))
                }.onFailure { onError(BiliFeature.OPEN_VIDEO_IN_NEW_TASK, "插入新任务按钮", it) }
            }
            if (added.isNotEmpty()) menuAction("更新视频操作菜单") {
                param.args[0] = added + (param.args[0] as List<*>)
            }
        }
        hooks += click.hookBefore { param ->
            val action = actions[param.thisObject] ?: return@hookBefore
            // 自定义点击实例不具备宿主 lambda 的捕获字段，即使失败也不能执行原始 invoke。
            param.result = unit
            val activity = action.menu.activity.get()
            if (action.enabled && activity != null) runCatching {
                val toolbar = action.toolbar.get()
                val close = toolbar?.javaClass?.declaredFields?.single { it.type == function0 }
                    ?.apply { isAccessible = true }?.get(toolbar)
                if (close != null) function0.getMethod("invoke").invoke(close)
                activity.window.decorView.post {
                    runCatching {
                        when (action.feature) {
                            BiliFeature.VIEW_COVER -> cover!!.show(activity, action.coverUrl!!)
                            BiliFeature.OPEN_VIDEO_IN_NEW_TASK -> openBiliVideoInNewTask(activity)
                            else -> showBiliSubtitleDialog(activity, action.menu.tracks)
                        }
                    }.onFailure { onError(action.feature, "打开视频工具", it) }
                }
            }.onFailure { onError(action.feature, "关闭视频操作菜单", it) }
        }
        hooks += render.hook(before = { disabled.set(items[it.args[0]] == true) }, after = { disabled.remove() })
        hooks += clickable.hook(before = { param ->
            if (disabled.get() == true) menuAction("禁用视频工具按钮") {
                param.args[1] = false
                param.args[3] = (param.args[3] as Int) and 1.inv()
            }
        }, after = { param ->
            if (disabled.get() == true && param.throwable == null) menuAction("显示按钮禁用状态") {
                param.result = alpha.invoke(null, param.result, 0.38f)
            }
        })
    }
}
