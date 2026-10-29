package io.github.a13e300.myinjector.config

import android.content.SharedPreferences
import io.github.a13e300.myinjector.Entry
import io.github.a13e300.myinjector.logE
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Java 读端复用框架缓存和监听，刷新与卸载互斥，不保留宿主文件。 */
class RemoteConfig<T>(
    private val group: String,
    private val parse: (ConfigValues) -> T,
    private val publish: (T) -> Unit,
) : AutoCloseable {
    private val lock = Any()
    private val worker = Executors.newSingleThreadScheduledExecutor()
    private val queued = AtomicBoolean()
    private var closed = false
    private var prefs: SharedPreferences? = null
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> request() }

    fun start() { refresh() }
    private fun request(delay: Long = 0) {
        synchronized(lock) {
            if (closed || !queued.compareAndSet(false, true)) return
            worker.schedule({ queued.set(false); refresh() }, delay, TimeUnit.MILLISECONDS)
        }
    }
    private fun refresh() {
        synchronized(lock) {
            if (closed) return
            try {
                val remote = prefs ?: Entry.instance.getRemotePreferences(group).also {
                    it.registerOnSharedPreferenceChangeListener(listener)
                    prefs = it
                }
                publish(parse(ConfigValues(remote.all)))
            } catch (error: Exception) {
                logE("remote config $group", error)
                request(3000)
            }
        }
    }
    override fun close() {
        synchronized(lock) {
            closed = true
            prefs?.unregisterOnSharedPreferenceChangeListener(listener)
            prefs = null
            worker.shutdownNow()
        }
    }
}
