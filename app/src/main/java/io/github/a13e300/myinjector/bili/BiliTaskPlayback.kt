package io.github.a13e300.myinjector.bili

import android.app.Activity
import android.content.Context
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.bridge.Unhook
import io.github.a13e300.myinjector.logI
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

internal class BiliTaskPlayback(
    private val loader: ClassLoader,
    private val members: Map<String, ObfsInfo>,
    private val isEnabled: () -> Boolean,
    private val onError: (String, Throwable) -> Unit,
) {
    private fun method(key: String) = DexMethod(members.getValue(key).descriptor).getMethodInstance(loader)
    private fun field(key: String) = DexField(members.getValue(key).descriptor).getFieldInstance(loader)
        .apply { isAccessible = true }

    private val keep = method("keep")
    private val container = field("container")
    private val getContext = container.type.getMethod("getContext")
    private val findActivity = loader.loadClass("kntr.base.android.legacy.context.ContextUtilKt")
        .getMethod("findActivityOrNull", Context::class.java)
    private val backgroundEnabled = keep.declaringClass.getMethod("isEnable")
    private val biliContext = loader.loadClass("com.bilibili.base.BiliContext")
    private val topActivity = biliContext.getMethod("topActivity")
    private val isVisible = biliContext.getMethod("isVisible")
    private val scopes = ThreadLocal<ArrayDeque<Any?>>()

    fun install(hooks: MutableList<Unhook>) {
        // 只覆盖两个跨页面观察回调中的保留判断，不改变其他地方的边看边玩状态。
        hooks += keep.hookAfter { param ->
            if (param.throwable != null || param.result != false || !isEnabled()) return@hookAfter
            val owner = param.args[0] ?: return@hookAfter
            if (scopes.get()?.lastOrNull() !== owner) return@hookAfter
            runCatching {
                if (shouldKeep(owner)) param.result = true
            }.onFailure { onError("判断独立任务后台播放资格", it) }
        }
        listOf("top" to "topOwner", "visible" to "visibleOwner").forEach { (entry, ownerKey) ->
            val owner = field(ownerKey)
            hooks += method(entry).hook(before = { param ->
                val scope = scopes.get() ?: ArrayDeque<Any?>().also { scopes.set(it) }
                scope.addLast(runCatching { owner.get(param.thisObject) }
                    .onFailure { onError("读取后台播放回调 ($entry)", it) }.getOrNull())
            }, after = {
                // 无论宿主抛异常还是开关中途改变，都清理范围，嵌套回调保留外层。
                scopes.get()?.let { scope ->
                    scope.removeLast()
                    if (scope.isEmpty()) scopes.remove()
                }
            })
        }
    }

    private fun shouldKeep(service: Any): Boolean {
        val context = getContext.invoke(container.get(service))
        val source = findActivity.invoke(null, context) as? Activity ?: return false
        if (source.isFinishing || source.isDestroyed) return false
        if (backgroundEnabled.invoke(service) != true) return false
        if (isVisible.invoke(null) != true) return false
        val target = topActivity.invoke(null) as? Activity ?: return false
        if (target.isFinishing || target.isDestroyed || source.packageName != target.packageName) return false
        val sourceTask = source.taskId
        val targetTask = target.taskId
        if (sourceTask < 0 || targetTask < 0 || sourceTask == targetTask) return false
        if (!isBiliVideoTask(source)) return false
        logI("bili keep new task playback: task=$sourceTask, topTask=$targetTask")
        return true
    }
}
