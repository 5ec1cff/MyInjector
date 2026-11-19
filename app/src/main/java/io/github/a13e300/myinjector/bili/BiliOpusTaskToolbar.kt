package io.github.a13e300.myinjector.bili

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.findBaseActivityOrNull
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.bridge.Unhook
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.ref.WeakReference
import java.lang.reflect.Proxy

/** 图文顶部入口，复用宿主按钮样式和当前页面的启动参数。 */
internal class BiliOpusTaskToolbar(
    private val loader: ClassLoader,
    private val enabled: () -> Boolean,
    private val onError: (String, Throwable) -> Unit,
) {
    private fun open(activity: Activity) {
        if (activity.isFinishing || activity.isDestroyed) return
        runCatching {
            val intent = Intent(activity.intent).setClass(activity, activity.javaClass).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_DOCUMENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                removeCategory(BILI_VIDEO_TASK)
            }
            activity.startActivity(intent)
            activity.finish()
        }.onFailure {
            onError("从图文顶部打开新任务", it)
            Toast.makeText(activity, "打开新任务失败", Toast.LENGTH_SHORT).show()
        }
    }

    fun install(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        runCatching { installCompose(creator, hooks) }
            .onFailure { onError("安装新版图文顶部按钮", it) }
        runCatching { installFragment(hooks) }
            .onFailure { onError("安装原生图文顶部按钮", it) }
    }

    private class Bar(var sharePainter: Any? = null)

    private fun installCompose(creator: ObfsTableCreator, hooks: MutableList<Unhook>) {
        val members = biliOpusTaskMembers(creator)
        fun method(key: String) = DexMethod(members.getValue(key).descriptor).getMethodInstance(loader)
            .apply { isAccessible = true }
        val icon = method("icon")
        val pill = method("pill")
        val composer = loader.loadClass("androidx.compose.runtime.Composer")
        val modifier = loader.loadClass("androidx.compose.ui.Modifier")
        val baseModifier = modifier.getField("Companion").get(null)
        val consume = composer.getMethod("consume", loader.loadClass("androidx.compose.runtime.CompositionLocal"))
        val localContext = loader.loadClass("androidx.compose.ui.platform.AndroidCompositionLocals_androidKt")
            .getMethod("getLocalContext").invoke(null)
        val start = composer.getMethod("startReplaceGroup", Int::class.javaPrimitiveType)
        val end = composer.getMethod("endReplaceGroup")
        val width = loader.loadClass("androidx.compose.foundation.layout.SizeKt")
            .getMethod("width-3ABfNKs", modifier, Float::class.javaPrimitiveType)
        val spacer = loader.loadClass("androidx.compose.foundation.layout.SpacerKt")
            .getMethod("Spacer", modifier, composer, Int::class.javaPrimitiveType)
        val description = loader.loadClass("kntr.common.a11y.meta.A11yMetaSemanticsKt")
            .getMethod("contentDescription", modifier, *Array(6) { String::class.java })
        val buttonModifier = description.invoke(null, baseModifier, TITLE, TITLE, null, null, null, null)
        val painterConstructor = loader.loadClass("com.google.accompanist.drawablepainter.DrawablePainter")
            .getConstructor(Drawable::class.java)
        var painter: Any? = null
        val function = loader.loadClass("kotlin.jvm.functions.Function0")
        val unit = loader.loadClass("kotlin.Unit").getField("INSTANCE").get(null)
        val scope = ThreadLocal<MutableList<Bar>>()
        // 图片头图的胶囊按钮栏有自己的重组入口，需要独立维护作用域。
        for (key in listOf("bar", "pillBar")) {
            hooks += method(key).hook(before = {
                val stack = scope.get() ?: mutableListOf<Bar>().also { scope.set(it) }
                stack.add(Bar())
            }, after = {
                scope.get()?.let { stack ->
                    stack.removeAt(stack.lastIndex)
                    if (stack.isEmpty()) scope.remove()
                }
            })
        }
        hooks += loader.loadClass("com.bilibili.compose.iconfont.BiliIconfont")
            .getMethod("getArrow_share_line_500", composer, Int::class.javaPrimitiveType).hookAfter {
                scope.get()?.lastOrNull()?.sharePainter = it.result
            }
        for (button in listOf(icon, pill)) {
            val isPill = button == pill
            hooks += button.hookBefore { param ->
                val current = scope.get()?.lastOrNull() ?: return@hookBefore
                val painterIndex = if (isPill) 0 else 4
                if (current.sharePainter == null || param.args[painterIndex] !== current.sharePainter) return@hookBefore
                val composition = param.args[if (isPill) 5 else 2]
                runCatching {
                    // 固定的分组保证开关变化时，新增按钮不会占用原分享按钮的状态。
                    start.invoke(composition, 0x13e30071)
                    try {
                        if (!enabled()) return@runCatching
                        val activity = (consume.invoke(composition, localContext) as? Context)
                            ?.findBaseActivityOrNull() ?: return@runCatching
                        val target = WeakReference(activity)
                        val click = Proxy.newProxyInstance(loader, arrayOf(function)) { proxy, called, args ->
                            when (called.name) {
                                "invoke" -> { target.get()?.let { open(it) }; unit }
                                "toString" -> TITLE
                                "hashCode" -> System.identityHashCode(proxy)
                                "equals" -> proxy === args?.firstOrNull()
                                else -> error("Unexpected button method: $called")
                            }
                        }
                        val image = painter ?: painterConstructor.newInstance(
                            BitmapDrawable(activity.resources, biliVideoTaskIcon(activity))
                        ).also { painter = it }
                        if (isPill) {
                            pill.invoke(null, image, TITLE, TITLE, click, false, composition, 0, 0)
                        } else {
                            icon.invoke(null, 0, 0, composition, buttonModifier, image, click, false, false)
                            spacer.invoke(null, width.invoke(null, baseModifier, 20f), composition, 0)
                        }
                    } finally {
                        end.invoke(composition)
                    }
                }.onFailure { onError("添加新版图文顶部按钮", it) }
            }
        }
    }

    private fun installFragment(hooks: MutableList<Unhook>) {
        val fragment = loader.loadClass("com.bilibili.bplus.followinglist.page.opus.OpusDetailFragment")
        val getActivity = fragment.getMethod("getActivity")
        hooks += fragment.getDeclaredMethod("onViewCreated", View::class.java, Bundle::class.java).hookAfter { param ->
            if (!enabled()) return@hookAfter
            runCatching {
                val activity = getActivity.invoke(param.thisObject) as? Activity ?: return@runCatching
                val root = param.args[0] as View
                fun id(name: String) = activity.resources.getIdentifier(name, "id", activity.packageName)
                val more = root.findViewById<ImageView>(id("opus_top_more"))
                    ?: error("找不到图文顶部菜单按钮")
                val parent = more.parent as ViewGroup
                if (parent.findViewWithTag<View>(TAG) != null) return@runCatching
                val dp = activity.resources.displayMetrics.density
                val button = ImageView(activity).apply {
                    this.id = View.generateViewId()
                    tag = TAG
                    contentDescription = TITLE
                    setImageDrawable(BitmapDrawable(resources, biliVideoTaskIcon(activity)))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val padding = (5 * dp).toInt()
                    setPadding(padding, padding, padding, padding)
                    setOnClickListener { open(activity) }
                }
                // ConstraintLayout 属于宿主的 ClassLoader，保持原标题和关注按钮的约束关系。
                val paramsClass = more.layoutParams.javaClass
                val params = paramsClass.getConstructor(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
                    .newInstance((35 * dp).toInt(), (30 * dp).toInt()) as ViewGroup.MarginLayoutParams
                paramsClass.getField("endToStart").setInt(params, more.id)
                paramsClass.getField("topToTop").setInt(params, 0)
                paramsClass.getField("bottomToBottom").setInt(params, 0)
                for (index in 0 until parent.childCount) {
                    val child = parent.getChildAt(index)
                    val original = child.layoutParams
                    if (paramsClass.getField("endToStart").getInt(original) == more.id) {
                        paramsClass.getField("endToStart").setInt(original, button.id)
                        child.layoutParams = original
                    }
                }
                parent.addView(button, params)
                parent.viewTreeObserver.addOnGlobalLayoutListener {
                    // 图片头图和滚动状态会改变原生图标颜色，跟随原菜单按钮。
                    button.imageTintList = more.imageTintList
                    button.drawable.colorFilter = more.drawable?.colorFilter
                    button.alpha = more.alpha
                }
            }.onFailure { onError("添加原生图文顶部按钮", it) }
        }
    }

    private companion object {
        const val TITLE = "在新任务打开"
        const val TAG = "myinjector_opus_new_task"
    }
}
