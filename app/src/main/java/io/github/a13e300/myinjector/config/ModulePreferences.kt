package io.github.a13e300.myinjector.config

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import io.github.a13e300.myinjector.logE
import io.github.libxposed.service.XposedService
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

/** remote 为权威存储；旧私有 prefs 仅作为离线缓存和持久化 outbox。 */
class ModulePreferences(context: Context) {
    val cache: SharedPreferences = context.getSharedPreferences("system_server", Context.MODE_PRIVATE)
    private val sync = PreferenceSync(cache)
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()
    @Volatile private var service: XposedService? = null
    @Volatile var status = "配置等待连接后同步"
        private set
    private var retry: ScheduledFuture<*>? = null // worker only

    fun addListener(listener: (String) -> Unit) {
        listeners.add(listener)
        listener(status)
    }
    fun removeListener(listener: (String) -> Unit) { listeners.remove(listener) }
    private fun report(value: String) {
        status = value
        main.post { listeners.forEach { it(status) } }
    }
    fun bind(value: XposedService?) {
        service = value
        syncNow()
    }
    fun putBoolean(key: String, value: Boolean) = put(key, value)
    fun putString(key: String, value: String) = put(key, value)
    private fun put(key: String, value: Any) {
        sync.put(key, value)
        report("配置待同步")
        syncNow()
    }
    private fun syncNow() {
        worker.execute {
            retry?.cancel(false)
            retry = null
            synchronize()
        }
    }
    private fun synchronize() {
        val connection = service
        if (connection == null) {
            report("未连接 · 修改已保存在本地，连接后自动同步")
            return
        }
        try {
            check(connection.frameworkProperties and XposedService.PROP_CAP_REMOTE != 0L) {
                "当前框架不支持远程配置"
            }
            report("正在同步配置…")
            for (group in SettingsSchema.groups.keys) {
                check(service === connection) { "模块服务已切换" }
                val remote = connection.getRemotePreferences(group)
                sync.sync(group, remote) { check(service === connection) { "模块服务已切换" } }
            }
            val pending = sync.hasPending()
            report(if (pending) "配置待同步" else "配置已同步")
            if (pending) retry = worker.schedule(::synchronize, 100, TimeUnit.MILLISECONDS)
        } catch (error: Exception) {
            logE("remote settings sync", error)
            report("配置未同步 · ${error.message ?: "连接失败"}，将自动重试")
            retry = worker.schedule(::synchronize, 3, TimeUnit.SECONDS)
        }
    }
}
