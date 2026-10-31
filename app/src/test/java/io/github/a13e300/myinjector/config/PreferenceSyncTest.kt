package io.github.a13e300.myinjector.config

import android.content.SharedPreferences
import org.junit.Assert.*
import org.junit.Test

class PreferenceSyncTest {
    private val key = "miuiHomeDragKill"

    @Test fun marketDefaultsArePublishedAndRemoteOptOutIsPreserved() {
        val cache = MemoryPrefs()
        val remote = MemoryPrefs()
        val sync = PreferenceSync(cache)
        sync.sync("market", remote)
        val keys = listOf("marketRemoveSplashAd", "marketSuperMinimalMode", "marketSimplifyUpdates")
        keys.forEach { assertEquals(true, remote.persisted[it]) }
        remote.edit().putBoolean("marketSuperMinimalMode", false).commit()
        sync.sync("market", remote)
        assertFalse(cache.getBoolean("marketSuperMinimalMode", true))
        assertTrue(cache.getBoolean("marketRemoveSplashAd", false))
        assertTrue(cache.getBoolean("marketSimplifyUpdates", false))
    }

    @Test fun migratesLegacyButPreservesExistingRemoteValues() {
        val local = MemoryPrefs(mutableMapOf(key to true, "miuiHomeOpenAospSettings" to true))
        val remote = MemoryPrefs(mutableMapOf(key to false))
        PreferenceSync(local).sync("miuihome", remote)
        assertFalse(remote.getBoolean(key, true))
        assertTrue(remote.getBoolean("miuiHomeOpenAospSettings", false))
        assertFalse(local.getBoolean(key, true))
        assertEquals(1, remote.getInt(SettingsSchema.VERSION_KEY, 0))
    }

    @Test fun failedCommitIsRetriedEvenWhenRemoteClientCacheAlreadyChanged() {
        val local = MemoryPrefs(mutableMapOf(key to true))
        val remote = MemoryPrefs().apply { succeeds = false }
        val sync = PreferenceSync(local)
        assertThrows(IllegalStateException::class.java) { sync.sync("miuihome", remote) }
        assertTrue(sync.hasPending())
        assertTrue(remote.getBoolean(key, false)) // 模拟 libxposed 先更新缓存再失败。
        assertTrue(remote.persisted.isEmpty())
        remote.succeeds = true
        sync.sync("miuihome", remote)
        assertEquals(true, remote.persisted[key])
        assertFalse(sync.hasPending())
    }

    @Test fun inFlightCompletionDoesNotEraseNewEditAndOutboxSurvivesRecreation() {
        val local = MemoryPrefs()
        val remote = MemoryPrefs(mutableMapOf(SettingsSchema.VERSION_KEY to 1))
        val sync = PreferenceSync(local)
        sync.put(key, true)
        remote.onCommit = { sync.put(key, false) }
        sync.sync("miuihome", remote)
        assertFalse(local.getBoolean(key, true))
        assertTrue(sync.hasPending())
        remote.onCommit = null
        val recreated = PreferenceSync(local)
        recreated.sync("miuihome", remote)
        assertEquals(false, remote.persisted[key])
        assertFalse(recreated.hasPending())
    }

    @Test fun reconnectDoesNotPublishStaleCacheOverInitializedRemote() {
        val local = MemoryPrefs(mutableMapOf(key to true))
        val remote = MemoryPrefs(mutableMapOf(SettingsSchema.VERSION_KEY to 1, key to false))
        PreferenceSync(local).sync("miuihome", remote)
        assertEquals(false, remote.persisted[key])
        assertFalse(local.getBoolean(key, true))
    }

    @Test fun clearingRemoteDoesNotResurrectPreviouslyMigratedCache() {
        val local = MemoryPrefs(mutableMapOf(key to true))
        val sync = PreferenceSync(local)
        sync.sync("miuihome", MemoryPrefs())
        val cleared = MemoryPrefs()
        sync.sync("miuihome", cleared)
        assertFalse(cleared.contains(key))
        assertFalse(local.getBoolean(key, true))
        assertFalse(sync.hasPending())
    }

    @Test fun serviceChangeAfterCommitRetainsOutboxForNewService() {
        val local = MemoryPrefs()
        val sync = PreferenceSync(local)
        sync.put(key, true)
        assertThrows(IllegalStateException::class.java) {
            sync.sync("miuihome", MemoryPrefs()) { error("service changed") }
        }
        assertTrue(sync.hasPending())
        val replacement = MemoryPrefs()
        sync.sync("miuihome", replacement)
        assertTrue(replacement.getBoolean(key, false))
        assertFalse(sync.hasPending())
    }

    @Test fun schemaExactlyMatchesEditableCatalog() {
        val items = io.github.a13e300.myinjector.ui.settings.moduleSettingsSections()
            .flatMap { it.items }
        val keys = items.filter { it is io.github.a13e300.myinjector.ui.settings.SettingsItemSpec.Switch ||
            it is io.github.a13e300.myinjector.ui.settings.SettingsItemSpec.Text }.map { it.key }.toSet()
        assertEquals(keys, SettingsSchema.defaults.keys)
        assertEquals(27, keys.size)
    }
}

/** commit 会先改变缓存，失败时不改变持久状态，重现 service 102 的关键行为。 */
private class MemoryPrefs(val values: MutableMap<String, Any> = mutableMapOf()) : SharedPreferences {
    var succeeds = true
    var onCommit: (() -> Unit)? = null
    var persisted: Map<String, Any> = values.toMap()
    override fun getAll(): Map<String, *> = values.toMap()
    override fun contains(key: String) = values.containsKey(key)
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun getString(key: String, defValue: String?) = values[key] as? String ?: defValue
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getStringSet(key: String, defValues: MutableSet<String>?) = defValues
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {}
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        val put = mutableMapOf<String, Any>()
        val remove = mutableSetOf<String>()
        var clear = false
        override fun putBoolean(key: String, value: Boolean) = apply { put[key] = value }
        override fun putString(key: String, value: String?) = apply { if (value == null) remove.add(key) else put[key] = value }
        override fun putInt(key: String, value: Int) = apply { put[key] = value }
        override fun putLong(key: String, value: Long) = apply { put[key] = value }
        override fun putFloat(key: String, value: Float) = apply { put[key] = value }
        override fun putStringSet(key: String, value: MutableSet<String>?) = apply { if (value == null) remove.add(key) else put[key] = value }
        override fun remove(key: String) = apply { remove.add(key) }
        override fun clear() = apply { clear = true }
        override fun commit(): Boolean {
            if (clear) values.clear()
            remove.forEach(values::remove)
            values.putAll(put)
            onCommit?.invoke()
            if (succeeds) persisted = values.toMap()
            return succeeds
        }
        override fun apply() { commit() }
    }
}
