package io.github.a13e300.myinjector.config

import android.content.SharedPreferences
import java.util.UUID

/** 值和 outbox token 共用一次本地编辑；远端确认只清除该批次的 token。 */
internal class PreferenceSync(private val cache: SharedPreferences) {
    private val lock = Any()
    fun put(key: String, value: Any) {
        require(SettingsSchema.defaults[key]?.javaClass == value.javaClass)
        synchronized(lock) {
            cache.edit().putValue(key, value)
                .putString(pendingKey(key), UUID.randomUUID().toString()).apply()
        }
    }
    fun hasPending(): Boolean = synchronized(lock) {
        SettingsSchema.defaults.keys.any { cache.contains(pendingKey(it)) }
    }
    fun sync(group: String, remote: SharedPreferences, checkCurrent: () -> Unit = {}) {
        val defaults = SettingsSchema.groups.getValue(group)
        val original = remote.all
        val batch: Map<String, Pair<String, Any>> = synchronized(lock) {
            // 导入缺失项时先写入 outbox；即使 commit 失败后 remote 缓存已改变，也会重试。
            if (!original.containsKey(SettingsSchema.VERSION_KEY) && !cache.getBoolean("__migrated:$group", false)) {
                val editor = cache.edit()
                for ((key, default) in defaults) {
                    if (!original.containsKey(key) && !cache.contains(pendingKey(key))) {
                        val value = cache.all[key]?.takeIf { it.javaClass == default.javaClass } ?: default
                        editor.putValue(key, value)
                        editor.putString(pendingKey(key), UUID.randomUUID().toString())
                    }
                }
                editor.apply()
            }
            defaults.keys.mapNotNull { key ->
                val token = cache.getString(pendingKey(key), null) ?: return@mapNotNull null
                key to (token to (cache.all[key] ?: defaults.getValue(key)))
            }.toMap()
        }
        val editor = remote.edit().putInt(SettingsSchema.VERSION_KEY, 1)
        batch.forEach { (key, change) -> editor.putValue(key, change.second) }
        check(editor.commit()) { "远程配置提交失败：$group" }
        checkCurrent()
        val saved = remote.all
        synchronized(lock) {
            val local = cache.edit().putBoolean("__migrated:$group", true)
            for ((key, default) in defaults) {
                val token = cache.getString(pendingKey(key), null)
                val sent = batch[key]?.first
                // 新的离线编辑不能被较早的提交结果清除或覆盖。
                if (token != null && token != sent) continue
                if (token != null) local.remove(pendingKey(key))
                val value = saved[key] ?: default
                check(value.javaClass == default.javaClass) { "配置类型错误：$key" }
                local.putValue(key, value)
            }
            local.apply()
        }
    }
    private fun pendingKey(key: String) = "__pending:$key"
    private fun SharedPreferences.Editor.putValue(key: String, value: Any): SharedPreferences.Editor =
        when (value) {
            is Boolean -> putBoolean(key, value)
            is String -> putString(key, value)
            else -> error("不支持的配置类型：$key")
        }
}
