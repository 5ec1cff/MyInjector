package io.github.a13e300.myinjector.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 设置数据与宿主业务回调分离；不依赖 Android Preference、反射或隐式持久化。 */
open class SettingItem(
    val key: String,
    title: CharSequence,
    summary: CharSequence? = null,
) {
    var title by mutableStateOf(title)
    var summary by mutableStateOf(summary)
    var isEnabled by mutableStateOf(true)
}

class ToggleSetting(key: String, title: CharSequence, summary: CharSequence? = null) :
    SettingItem(key, title, summary) {
    var isChecked by mutableStateOf(false)

    fun requestChange(value: Boolean, accept: (Boolean) -> Boolean): Boolean {
        if (!isEnabled || value == isChecked) return false
        if (!accept(value)) return false
        isChecked = value
        return true
    }
}

class SettingsGroup(key: String = "root", title: CharSequence = "") : SettingItem(key, title) {
    val items = mutableListOf<SettingItem>()

    fun find(key: String): SettingItem = flattened().first { it.key == key }

    fun flattened(): List<SettingItem> = items.flatMap {
        if (it is SettingsGroup) listOf(it) + it.flattened() else listOf(it)
    }

    fun toggle(
        title: String, key: String, summary: String? = null,
        configure: ToggleSetting.() -> Unit = {},
    ) {
        items += ToggleSetting(key, title, summary).apply(configure)
    }

    fun action(title: String, key: String, summary: String? = null) {
        items += SettingItem(key, title, summary)
    }

    fun group(title: String, configure: SettingsGroup.() -> Unit) {
        items += SettingsGroup("group:$title", title).apply(configure)
    }
}

/** 空查询保留分组；搜索按标题、说明、配置键评分，稳定保留同分项的原始顺序。 */
fun SettingsGroup.search(query: String): List<SettingItem> {
    val normalized = query.trim()
    val all = flattened()
    if (normalized.isEmpty()) return all
    return all.filterNot { it is SettingsGroup }.map { item ->
        val score = (if (item.title.contains(normalized, true)) 12 else 0) +
            (if (item.summary?.contains(normalized, true) == true) 6 else 0) +
            (if (item.key.contains(normalized, true)) 2 else 0)
        item to score
    }.filter { it.second > 0 }.sortedByDescending { it.second }.map { it.first }
}
