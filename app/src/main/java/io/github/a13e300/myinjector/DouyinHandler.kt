package io.github.a13e300.myinjector

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.TextView
import io.github.a13e300.myinjector.arch.IHook
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.findBaseActivityOrNull
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookAllBefore
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.douyin.DouyinImageSave
import io.github.a13e300.myinjector.douyin.DouyinImageSource
import io.github.a13e300.myinjector.douyin.locateDouyinEmojiDetail
import java.io.Serializable
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import org.luckypray.dexkit.wrap.DexMethod

/** 评论表情使用抖音原生单图预览，保留动画并提供图片保存。 */
class DouyinHandler : IHook() {
    override fun onHook() {
        if (loadPackageParam.processName != loadPackageParam.packageName) return

        val emojiClass = findClass("com.ss.android.ugc.aweme.emoji.model.Emoji")
        val imageClass = findClass("com.ss.android.ugc.aweme.comment.model.CommentImageStruct")
        val urlClass = findClass("com.ss.android.ugc.aweme.base.model.UrlModel")
        val activityClass = findClass("com.ss.android.ugc.aweme.comment.ui.ImageDetailActivity")
        val imageSource = DouyinImageSource()
        val commentDetail = ObfsTableCreator("douyin_emoji", 1,
            appInfo = loadPackageParam.appInfo).use { creator ->
            creator.create("detail") { locateDouyinEmojiDetail(it) }.also { creator.persist() }
        }
        val detailMethod = DexMethod(commentDetail.descriptor).getMethodInstance(classLoader)
        val detailParams = detailMethod.parameterTypes.single()
        val scene = detailParams.declaredFields.single { it.type.isEnum }.accessible()
        val emoji = detailParams.declaredFields.single { it.type == emojiClass }.accessible()
        val context = detailParams.declaredFields.single { it.type == Context::class.java }.accessible()
        val animateUrl = emojiClass.serializedField("animate_url")
        val staticUrl = emojiClass.serializedField("static_url")
        val imageUrls = listOf("origin_url", "crop_url", "medium_url", "thumb_url", "download_url")
            .map { imageClass.serializedField(it) }
        val urlFields = urlClass.declaredFields.filter { !Modifier.isStatic(it.modifiers) }
            .onEach { it.isAccessible = true }
        val urls = urlClass.getDeclaredField("urlList").accessible()
        val downloadUrls = urlClass.getDeclaredField("downUrlList").accessible()
        val resourceUri = urlClass.getDeclaredField("uri").accessible()
        val width = urlClass.getDeclaredField("width").accessible()
        val height = urlClass.getDeclaredField("height").accessible()
        val emojiWidth = emojiClass.serializedField("width")
        val emojiHeight = emojiClass.serializedField("height")

        fun open(context: Context?, value: Any?): Boolean {
            if (!emojiClass.isInstance(value)) return false
            val activity = context?.findBaseActivityOrNull() ?: return false
            if (activity.isFinishing || activity.isDestroyed) return false
            val source = sequenceOf(animateUrl.get(value), staticUrl.get(value)).filterNotNull()
                .firstOrNull { !(urls.get(it) as? List<*>).isNullOrEmpty() } ?: return false
            val downloads = (downloadUrls.get(source) as? List<*>)
                ?.filterIsInstance<String>()?.filter { it.isNotBlank() }.orEmpty()
            val original = (resourceUri.get(source) as? String)?.takeIf {
                Uri.parse(it).scheme in setOf("http", "https")
            }
            val saveUrls = downloads.ifEmpty {
                if (original != null) listOf(original)
                else (urls.get(source) as? List<*>)?.filterIsInstance<String>().orEmpty()
            }
            // 只在同一资源的 CDN 地址间重试，不降级到另一种格式或尺寸。
            val path = saveUrls.firstOrNull()?.let { Uri.parse(it).encodedPath }
            val candidates = saveUrls.filter { Uri.parse(it).encodedPath == path }.distinct()
            // 复制 URL 模型，尺寸补全不能改动评论列表正在使用的对象。
            val url = urlClass.getDeclaredConstructor().newInstance()
            urlFields.forEach { it.set(url, it.get(source)) }
            if (width.getInt(url) <= 0) width.setInt(url, emojiWidth.getInt(value))
            if (height.getInt(url) <= 0) height.setInt(url, emojiHeight.getInt(value))
            val image = imageClass.getDeclaredConstructor().newInstance()
            imageUrls.forEach { it.set(image, url) }
            val intent = Intent(activity, activityClass)
                .putExtra("key_image", image as Serializable)
                .putExtra("key_enter_from", "comment")
                .putExtra(PREVIEW, true)
                .putStringArrayListExtra(SAVE_URLS, ArrayList(candidates))
            activity.startActivity(intent)
            return true
        }

        // 半屏表情详情入口，按场景限定到评论，聊天和搜索的表情详情保持原样。
        detailMethod.hookBefore { param ->
            val data = param.args[0] ?: return@hookBefore
            if ((scene.get(data) as? Enum<*>)?.name != "COMMENT") return@hookBefore
            runCatching { open(context.get(data) as? Context, emoji.get(data)) }
                .onSuccess { if (it) param.result = null }
                .onFailure { logE("open Douyin comment emoji", it) }
        }

        // 旧组件和旧列表通过 SmartRoute 打开表情 Activity。
        val routeClass = findClass("com.bytedance.router.SmartRoute")
        val routePath = routeClass.declaredFields.single { it.type == String::class.java }.accessible()
        val routeIntent = routeClass.declaredFields.single { it.type == Intent::class.java }.accessible()
        val routeContext = routeClass.declaredFields.single { it.type == Context::class.java }.accessible()
        routeClass.hookAllBefore("open") { param ->
            val route = param.thisObject
            if (routePath.get(route) != "//gif_emoji") return@hookAllBefore
            val intent = routeIntent.get(route) as? Intent ?: return@hookAllBefore
            if (!intent.hasExtra("commentMobParameters")) return@hookAllBefore
            @Suppress("DEPRECATION")
            val value = intent.getSerializableExtra("gif_emoji")
            runCatching { open(routeContext.get(route) as? Context, value) }
                .onSuccess { if (it) param.result = null }
                .onFailure { logE("open Douyin comment emoji route", it) }
        }

        activityClass.hookAfter("onCreate", Bundle::class.java) { param ->
            val activity = param.thisObject as Activity
            if (!activity.intent.getBooleanExtra(PREVIEW, false)) return@hookAfter
            val candidates = activity.intent.getStringArrayListExtra(SAVE_URLS).orEmpty()
            val content = activity.findViewById<FrameLayout>(android.R.id.content)
            val density = activity.resources.displayMetrics.density
            fun dp(value: Int) = (value * density).toInt()
            val save = TextView(activity).apply {
                text = "保存图片"
                contentDescription = text
                textSize = 15f
                setTextColor(Color.WHITE)
                setPadding(dp(20), dp(12), dp(20), dp(12))
                background = GradientDrawable().apply {
                    setColor(0xCC333333.toInt())
                    cornerRadius = dp(24).toFloat()
                }
                setOnClickListener { DouyinImageSave.save(activity, this, candidates, imageSource) }
                setOnApplyWindowInsetsListener { view, insets ->
                    @Suppress("DEPRECATION")
                    (view.layoutParams as FrameLayout.LayoutParams).apply {
                        bottomMargin = dp(24) + insets.systemWindowInsetBottom
                    }.also { view.layoutParams = it }
                    insets
                }
            }
            content.addView(save, FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            ).apply { bottomMargin = dp(48) })
            save.requestApplyInsets()
        }
    }

    private fun Field.accessible() = apply { isAccessible = true }

    private fun Class<*>.serializedField(name: String): Field = declaredFields.single { field ->
        field.declaredAnnotations.any { annotation ->
            annotation.annotationClass.java.name == "com.google.gson.annotations.SerializedName" &&
                annotation.annotationClass.java.getMethod("value").invoke(annotation) == name
        }
    }.accessible()

    private companion object {
        const val PREVIEW = "io.github.a13e300.myinjector.douyin.EMOJI_PREVIEW"
        const val SAVE_URLS = "io.github.a13e300.myinjector.douyin.SAVE_URLS"
    }
}
