package io.github.a13e300.myinjector.bili

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.toObfsInfo
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

internal fun locateBiliCover(bridge: DexKitBridge): Map<String, ObfsInfo> {
    val intent = bridge.findMethod { matcher {
        declaredClass("com.bilibili.bplus.baseplus.activity.ImagesViewerActivity")
        paramTypes("androidx.fragment.app.FragmentActivity", "java.util.ArrayList", "java.util.ArrayList",
            "com.bilibili.bplus.imageviewer.CropType", "android.graphics.Rect")
        returnType("android.content.Intent")
    } }.single()
    return mapOf("intent" to intent.toObfsInfo())
}

internal fun biliCoverMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { locateBiliCover(creator.bridge) }
    return listOf("intent").associateWith { key ->
        creator.create("cover.$key") { found.getValue(key) }
    }
}

internal class BiliCover(private val loader: ClassLoader, private val members: Map<String, ObfsInfo>, toolbar: ObfsInfo) {
    private val container = DexField(toolbar.descriptor).getFieldInstance(loader).apply { isAccessible = true }
    private val playDirector = loader.loadClass("tv.danmaku.biliplayerv2.PlayerContainerKt")
        .getMethod("getPlayDirector", loader.loadClass("tv.danmaku.biliplayerv2.IPlayerContainer"))
    private val directorType = loader.loadClass("tv.danmaku.biliplayerv2.service.IPlayDirector")
    private val currentParams = directorType.getMethod("getCurrentPlayableParams")
    private val playable = loader.loadClass("com.bilibili.app.gemini.base.player.GeminiCommonPlayableParams")
    private val getCover = playable.getMethod("getCover")
    private val createIntent = DexMethod(members.getValue("intent").descriptor).getMethodInstance(loader)
    val onCreate = createIntent.declaringClass.getMethod("onCreate", Bundle::class.java)
    private val cropType = loader.loadClass("com.bilibili.bplus.imageviewer.CropType").getField("CENTER").get(null)
    private val image = loader.loadClass("com.bilibili.bplus.imageviewer.ImageInfo").getConstructor(
        String::class.java, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, String::class.java, String::class.java)

    fun snapshot(toolbar: Any): String? {
        val params = currentParams.invoke(playDirector.invoke(null, container.get(toolbar)))
        if (!playable.isInstance(params)) return null
        return (getCover.invoke(params) as? String)?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("//")) "https:$it" else it }
    }

    fun show(activity: Activity, url: String) {
        val item = image.newInstance(url, 0, 0, 0, url, url)
        // 使用带保存按钮的内置查看器，下载和权限处理都交给宿主。
        val intent = createIntent.invoke(null, activity, arrayListOf(item), null, cropType, null) as Intent
        intent.setClass(activity, createIntent.declaringClass)
        intent.putExtra(COVER_VIEWER, true)
        activity.startActivity(intent)
    }

    fun onCreated(activity: Activity) {
        if (!activity.intent.getBooleanExtra(COVER_VIEWER, false)) return
        // 布局默认 alpha=0，没有缩放起点时宿主不会播放显示按钮栏的动画。
        val id = activity.resources.getIdentifier("bottom_layout", "id", activity.packageName)
        activity.findViewById<View>(id).alpha = 1f
    }

    companion object {
        private const val COVER_VIEWER = "io.github.a13e300.myinjector.VIEW_VIDEO_COVER"
    }
}
