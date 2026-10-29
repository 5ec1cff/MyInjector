package io.github.a13e300.myinjector.scope

import android.os.Handler
import android.os.Looper
import io.github.libxposed.service.XposedService
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors

/** 状态仅在主线程修改；IPC 串行执行。授权结果必须重新查询，不乐观开启。 */
internal interface ScopeBackend {
    fun getScope(): Set<String>
    fun request(packageName: String, result: (Boolean, String?) -> Unit)
    fun remove(packageName: String)
}

class ScopeManager internal constructor(
    private val postMain: (() -> Unit) -> Unit,
    private val execute: (() -> Unit) -> Unit,
) {
    constructor() : this(mainDispatcher(), workerDispatcher())

    data class State(
        val enabled: Set<String> = emptySet(),
        val pending: Set<String> = emptySet(),
        val ready: Boolean = false,
        val message: String = "连接模块服务后可管理作用域",
    )
    private val listeners = CopyOnWriteArraySet<(State) -> Unit>()
    @Volatile private var service: ScopeBackend? = null
    private var generation = 0
    var state = State()
        private set
    fun addListener(listener: (State) -> Unit) { listeners.add(listener); listener(state) }
    fun removeListener(listener: (State) -> Unit) { listeners.remove(listener) }
    private fun publish(value: State) { state = value; listeners.forEach { it(value) } }
    fun bind(value: XposedService?) = bindBackend(value?.let { connection ->
        object : ScopeBackend {
            override fun getScope() = connection.scope.toSet()
            override fun remove(packageName: String) = connection.removeScope(listOf(packageName))
            override fun request(packageName: String, result: (Boolean, String?) -> Unit) {
                connection.requestScope(listOf(packageName), object : XposedService.OnScopeEventListener {
                    override fun onScopeRequestApproved(approved: List<String>) = result(packageName in approved, null)
                    override fun onScopeRequestFailed(message: String) = result(false, message)
                })
            }
        }
    })
    internal fun bindBackend(value: ScopeBackend?) = onMain {
        generation++
        service = value
        publish(State(message = if (value == null) "连接模块服务后可管理作用域" else "正在读取作用域…"))
        refresh()
    }
    fun refresh() = onMain {
        val connection = service ?: return@onMain
        val current = generation
        execute {
            val result = runCatching { connection.getScope() }
            postMain {
                if (current == generation) result.fold(
                    { publish(state.copy(enabled = it, ready = true,
                        message = if (state.pending.isEmpty()) "作用域变更后，重启目标应用使其生效" else state.message)) },
                    { publish(state.copy(ready = false, message = "读取作用域失败：${it.message}")) },
                )
            }
        }
    }
    fun setEnabled(packageName: String, enabled: Boolean) = onMain {
        val connection = service ?: return@onMain
        if (!state.ready || state.pending.isNotEmpty() || (packageName in state.enabled) == enabled) return@onMain
        val current = generation
        publish(state.copy(pending = state.pending + packageName,
            message = if (enabled) "等待框架授权，请查看框架通知" else "正在关闭作用域…"))
        fun finish(error: String? = null) {
            postMain {
                if (current != generation) return@postMain
                publish(state.copy(message = error ?: "正在刷新作用域…"))
                // 查询失败不把旧值当作已确认；操作结果仍显示给用户。
                execute {
                    val result = runCatching { connection.getScope() }
                    postMain {
                        if (current == generation) result.fold(
                            { publish(state.copy(enabled = it, ready = true, pending = state.pending - packageName,
                                message = error ?: "作用域已更新，重启目标应用使其生效")) },
                            { publish(state.copy(ready = false, pending = state.pending - packageName, message = error ?: "读取作用域失败：${it.message}")) },
                        )
                    }
                }
            }
        }
        execute {
            if (service !== connection) return@execute
            try {
                if (enabled) connection.request(packageName) { approved, message ->
                    finish(if (approved) null else "作用域请求未获准：${message ?: "用户未授权"}")
                } else {
                    connection.remove(packageName)
                    finish()
                }
            } catch (error: Exception) { finish("作用域操作失败：${error.message}") }
        }
    }
    private fun onMain(action: () -> Unit) = postMain(action)
    companion object {
        private fun mainDispatcher(): (() -> Unit) -> Unit {
            val handler = Handler(Looper.getMainLooper())
            return { action ->
                if (Looper.myLooper() == Looper.getMainLooper()) action()
                else { handler.post { action() } }
            }
        }
        private fun workerDispatcher(): (() -> Unit) -> Unit {
            val executor = Executors.newSingleThreadExecutor()
            return { action -> executor.execute(action) }
        }
    }
}
