package io.github.a13e300.myinjector.bili

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.findBaseActivity
import io.github.a13e300.myinjector.logI
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.WeakHashMap

internal class BiliSubtitles(private val classLoader: ClassLoader, private val members: Map<String, ObfsInfo>) {
    private fun field(key: String) = DexField(members.getValue(key).descriptor)
        .getFieldInstance(classLoader).apply { isAccessible = true }
    private fun method(key: String) = DexMethod(members.getValue(key).descriptor)
        .getMethodInstance(classLoader).apply { isAccessible = true }
    val menu = method("menu")
    val click = method("click")
    val dismiss = method("dismiss")
    val content = method("content")
    val clickable = method("clickable")
    val filter = method("filter")
    val menuMethods = listOf(menu)
    val clickMethods = listOf(click)
    val dismissMethods = listOf(dismiss)
    private val toolbarContext = field("toolbarContext")
    private val toolbarContainer = field("toolbarContainer")
    private val legacyContainer = field("legacyContainer")
    val legacyService = legacyContainer.declaringClass
    private val channelItems = field("channelItems")
    private val owner = field("callbackOwner")
    private val ownerMedia = field("ownerMedia")
    private val serviceMedia = field("serviceMedia")
    private val ownerContext = field("ownerContext")
    private val state = method("state")
    private val loading = field("loading")
    private val error = field("error")
    private val reply = field("reply")
    val subtitleService = reply.declaringClass
    private val target = field("target")
    private val itemConstructor = target.declaringClass.declaredConstructors.single {
        it.parameterCount == 10 && it.parameterTypes.last() == String::class.java
    }
        .apply { isAccessible = true }
    private val vertexType = ownerContext.type
    private val getLocal = vertexType.methods.single { it.name == "getCompositionLocal" }
    private val localContext = classLoader.loadClass("androidx.compose.ui.platform.AndroidCompositionLocals_androidKt")
        .getMethod("getLocalContext").invoke(null)
    private val asImageBitmap = classLoader.loadClass("androidx.compose.ui.graphics.AndroidImageBitmap_androidKt")
        .getMethod("asImageBitmap", Bitmap::class.java)
    private val alpha = classLoader.loadClass("androidx.compose.ui.draw.AlphaKt")
        .getMethod("alpha", clickable.returnType, Float::class.javaPrimitiveType)
    private val services = Collections.synchronizedMap(WeakHashMap<Any, WeakReference<Any>>())
    data class Menu(val activity: WeakReference<Activity>, val tracks: List<BiliSubtitleTrack>, val label: String)
    private val menus = Collections.synchronizedMap(WeakHashMap<Any, Menu>())
    private val pending = Collections.synchronizedMap(WeakHashMap<Any, Menu>())
    private val disabled = ThreadLocal.withInitial { mutableListOf<Boolean>() }

    private fun Any.get(name: String) = javaClass.getMethod(name).invoke(this)

    fun attach(service: Any) {
        services[serviceMedia.get(service)!!] = WeakReference(service)
    }

    fun menuCallback(receiver: Any): Any? = receiver.takeIf { owner.declaringClass.isInstance(it) }
    fun dismissCallback(receiver: Any): Any? = menuCallback(receiver)

    fun attachLegacy(service: Any) {
        val container = legacyContainer.get(service) ?: return
        services[container.get("getPlayerSettingService")!!] = WeakReference(service)
    }

    fun insert(callback: Any, groups: List<List<Any>>): List<List<Any>> {
        val snapshot = modernSnapshot(callback)
        val itemTarget = if (snapshot.tracks.isEmpty()) DISABLED_TARGET else TARGET
        val item = itemConstructor.newInstance(itemTarget, snapshot.label, icon(snapshot.activity.get()!!), null, null, null, null, null, null, null)
        menus[callback] = snapshot
        // 第二行是操作区，先复制再插入，保留宿主列表和其他操作的顺序。
        val rows = groups.map { row -> row.filterNot { isSubtitleItem(it) } }.toMutableList()
        while (rows.size < 2) rows.add(emptyList())
        rows[1] = listOf(item) + rows[1]
        logI("bili subtitle menu inserted: callback=${callback.javaClass.name}, tracks=${snapshot.tracks.size}, label=${snapshot.label}")
        return rows
    }

    private fun modernSnapshot(callback: Any): Menu {
        val page = owner.get(callback)!!
        val context = getLocal.invoke(ownerContext.get(page), localContext) as Context
        val activity = context.findBaseActivity()
        val service = services[ownerMedia.get(page)]?.get()
        val current = service?.let { state.invoke(it) }
        val isLoading = current == null || loading.getBoolean(current)
        val failed = current != null && error.get(current) != null
        val subtitle = if (isLoading || failed) null else reply.get(service)?.get("getSubtitle")
        val tracks = tracks(subtitle?.get("getSubtitles") as? List<*>)
        return Menu(WeakReference(activity), tracks, when {
            isLoading -> "字幕加载中"
            failed -> "字幕暂不可用"
            tracks.isEmpty() -> "没有字幕"
            else -> "查看字幕"
        })
    }

    fun toolbarSnapshot(toolbar: Any): Menu {
        val container = toolbarContainer.get(toolbar)!!
        return legacyMenu(toolbarContext.get(toolbar) as Context,
            services[container.get("getPlayerSettingService")]?.get())
    }

    private fun legacyMenu(context: Context, service: Any?): Menu {
        val activity = context.findBaseActivity()
        val reply = service?.get("getSubtitleReply")
        val subtitle = reply?.get("getSubtitle")
        val tracks = tracks(subtitle?.get("getSubtitlesList") as? List<*>)
        return Menu(WeakReference(activity), tracks, when {
            reply == null -> "字幕加载中"
            tracks.isEmpty() -> "没有字幕"
            else -> "查看字幕"
        })
    }

    private fun tracks(source: List<*>?): List<BiliSubtitleTrack> = source?.mapNotNull { raw ->
        val track = raw ?: return@mapNotNull null
        val url = track.get("getSubtitleUrl") as String
        if (url.isBlank()) return@mapNotNull null
        val title = (track.get("getLanDoc") as String).ifBlank { track.get("getLan") as String }
        val format = track.get("getFormat")!!
        BiliSubtitleTrack(title, url, if (format is Enum<*>) format.name else format.get("getName") as String)
    }.orEmpty()

    fun onClick(callback: Any, item: Any): Boolean {
        if (!isSubtitleItem(item)) return false
        val snapshot = menus[callback] ?: return true
        if (snapshot.tracks.isNotEmpty()) pending[callback] = snapshot
        return true
    }

    fun onDismiss(callback: Any) {
        val snapshot = pending.remove(callback) ?: return
        snapshot.activity.get()?.let { activity ->
            activity.runOnUiThread { showBiliSubtitleDialog(activity, snapshot.tracks) }
        }
    }

    fun allowChannels(args: Array<Any?>) {
        @Suppress("UNCHECKED_CAST")
        val rows = channelItems.get(args[0]) as List<List<Any>>
        val names = rows.flatten().filter(::isSubtitleItem).map { target.get(it) as String }
        if (names.isNotEmpty()) {
            // 宿主在菜单回调之后过滤自定义渠道，只登记这次列表中实际添加的字幕按钮。
            args[1] = ((args[1] as List<*>) + names).distinct()
        }
    }

    fun enterItem(item: Any) {
        disabled.get()!!.add(target.get(item) == DISABLED_TARGET)
    }

    fun leaveItem() {
        val stack = disabled.get() ?: return
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        if (stack.isEmpty()) disabled.remove()
    }

    fun disableClick(args: Array<Any?>) {
        if (disabled.get()?.lastOrNull() != true) return
        args[3] = false
        // 清除默认参数位，否则 clickable 会把 enabled 改回 true。
        args[7] = (args[7] as Int) and 4.inv()
    }

    fun dim(result: Any?): Any? = if (disabled.get()?.lastOrNull() == true && result != null)
        alpha.invoke(null, result, 0.38f) else result

    private fun isSubtitleItem(item: Any): Boolean {
        val name = target.get(item)
        return name == TARGET || name == DISABLED_TARGET
    }

    private fun icon(activity: Activity): Any {
        val bitmap = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val night = activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (night) Color.WHITE else Color.rgb(45, 45, 45)
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        canvas.drawRoundRect(14f, 26f, 82f, 72f, 6f, 6f, paint)
        paint.style = Paint.Style.FILL
        paint.textSize = 27f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("CC", 48f, 58f, paint)
        return asImageBitmap.invoke(null, bitmap)!!
    }

    companion object {
        private const val TARGET = "MYINJECTOR_VIEW_SUBTITLES"
        private const val DISABLED_TARGET = "MYINJECTOR_SUBTITLES_UNAVAILABLE"
    }
}
