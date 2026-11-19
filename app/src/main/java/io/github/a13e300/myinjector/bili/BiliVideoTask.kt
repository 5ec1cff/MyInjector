package io.github.a13e300.myinjector.bili

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.os.Build

internal const val BILI_VIDEO_TASK = "io.github.a13e300.myinjector.BILI_VIDEO_TASK"

internal fun biliVideoTaskIcon(activity: Activity, night: Boolean = false): String {
    val suffix = if (night) "_night" else ""
    val file = activity.cacheDir.resolve("myinjector_openVideoInNewTask${suffix}_icon.png")
    if (file.isFile) return file.absolutePath
    val bitmap = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888)
    try {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (night) Color.WHITE else Color.BLACK
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }
        Canvas(bitmap).apply {
            drawRoundRect(6f, 14f, 34f, 42f, 3f, 3f, paint)
            drawLine(18f, 30f, 42f, 6f, paint)
            drawLine(28f, 6f, 42f, 6f, paint)
            drawLine(42f, 6f, 42f, 20f, paint)
        }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    } finally {
        bitmap.recycle()
    }
    return file.absolutePath
}

/** 让宿主路由准备启动参数，再把所选内容作为新任务的根页面打开。 */
internal class BiliVideoTaskRouter(loader: ClassLoader) {
    private val picture by lazy { BiliPictureTaskRouter(loader) }
    private val request = loader.loadClass("com.bilibili.lib.blrouter.RouteRequest")
    private val builder = loader.loadClass("com.bilibili.lib.blrouter.RouteRequest\$Builder")
    private val constructor = builder.getConstructor(String::class.java)
    private val build = builder.getMethod("build")
    private val mode = loader.loadClass("com.bilibili.lib.blrouter.RequestMode")
    private val intentMode = mode.getField("INTENT").get(null)
    private val newCall = loader.loadClass("com.bilibili.lib.blrouter.BLRouter").getMethod("newCall",
        request, Context::class.java, loader.loadClass("androidx.fragment.app.Fragment"),
        mode, Boolean::class.javaPrimitiveType)
    private val execute = loader.loadClass("com.bilibili.lib.blrouter.RouteCall").getMethod("execute")
    private val response = loader.loadClass("com.bilibili.lib.blrouter.RouteResponse")
    private val success = response.getMethod("isSuccess")
    private val result = response.getMethod("getObj")
    private val code = response.getMethod("getCode")
    private val message = response.getMethod("getMessage")

    fun open(activity: Activity, url: String) {
        if (activity.isFinishing || activity.isDestroyed) return
        val route = build.invoke(constructor.newInstance(url))
        val video = url.startsWith("bilibili://video/")
        val prepared = if (video) {
            val reply = execute.invoke(newCall.invoke(null, route, activity, null, intentMode, false))
            check(success.invoke(reply) == true) {
                "卡片路由失败: ${code.invoke(reply)}, ${message.invoke(reply)}, $url"
            }
            result.invoke(reply) as Intent
        } else {
            picture.createIntent(activity, route)
        }
        val intent = Intent(prepared).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
            // 图文任务不参与独立视频任务的后台播放豁免。
            if (video) addCategory(BILI_VIDEO_TASK)
        }
        activity.startActivity(intent)
    }
}

/** 图文的 CMP 重定向不能返回 Intent，直接使用宿主原生 Fragment 容器。 */
private class BiliPictureTaskRouter(loader: ClassLoader) {
    private val request = loader.loadClass("com.bilibili.lib.blrouter.RouteRequest")
    private val info = loader.loadClass("com.bilibili.lib.blrouter.RouteInfo")
    private val find = loader.loadClass("com.bilibili.lib.blrouter.BLRouter").getMethod("findRoutes", request)
    private val getInfo = loader.loadClass("com.bilibili.lib.blrouter.MatchedRoutes").getMethod("getInfo")
    private val getClass = info.getMethod("getClazz")
    private val extras = loader.loadClass("com.bilibili.routeui.launcher.AbstractLauncherKt")
        .getMethod("createExtrasForFragment", request, info)
    private val container = loader.loadClass("com.bilibili.lib.ui.ComposeActivity")
    private val fragments = setOf(
        "com.bilibili.bplus.followinglist.page.opus.OpusDetailFragment",
        "com.bilibili.bplus.followinglist.detail.DynamicDetailFragment",
    )

    fun createIntent(activity: Activity, route: Any): Intent {
        // findRoutes 只匹配原始地址，不执行转向 CMP 的灰度拦截器。
        val routes = getInfo.invoke(find.invoke(null, route)) as List<*>
        val target = routes.single { (getClass.invoke(it) as Class<*>).name in fragments }
        val fragment = getClass.invoke(target) as Class<*>
        // 与宿主 Fragment Launcher 相同，保留路径变量、query、props 和原始 URL。
        val args = extras.invoke(null, route, target) as Bundle
        return Intent(activity, container).putExtra("fragment", fragment.name).putExtra("fragment_args", args)
    }
}

internal fun openBiliVideoInNewTask(activity: Activity) {
    if (activity.isFinishing || activity.isDestroyed) return
    val intent = Intent(activity.intent).setClass(activity, activity.javaClass).apply {
        // 不继承原启动的栈操作标记，保留 data、extras 等详情页参数。
        flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
        // 系统任务快照会清理 extras，category 可保留在根 Intent 中。
        addCategory(BILI_VIDEO_TASK)
    }
    // 启动请求成功后再结束旧页面，启动异常时保留原页面供用户继续操作。
    activity.startActivity(intent)
    activity.finish()
}

internal fun isBiliVideoTask(activity: Activity): Boolean {
    val taskId = activity.taskId
    if (taskId < 0) return false
    val manager = activity.getSystemService(ActivityManager::class.java)
    return manager.appTasks.any { task ->
        val info = task.taskInfo ?: return@any false
        @Suppress("DEPRECATION")
        val id = if (Build.VERSION.SDK_INT >= 29) info.taskId else info.id
        id == taskId && info.baseIntent.hasCategory(BILI_VIDEO_TASK)
    }
}
