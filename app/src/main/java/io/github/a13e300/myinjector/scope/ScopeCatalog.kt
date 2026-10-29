package io.github.a13e300.myinjector.scope

import android.content.pm.ApplicationInfo

/** 列表展示与导航共用的应用信息；system 是框架作用域名，对应 PM 的 android 包。 */
internal data class ScopeApplication(val packageName: String, val label: String, val isSystemApp: Boolean) {
    val sectionKey: String? get() = ScopeCatalog.components.entries.firstOrNull { it.value == packageName }?.key
    val hasSettings: Boolean get() = packageName in ScopeCatalog.settingsApps
}

object ScopeCatalog {
    val components = mapOf("system" to "system", "systemui" to "com.android.systemui", "miuihome" to "com.miui.home")
    val settingsApps = setOf(
        "com.xingin.xhs", "com.kiwibrowser.browser", "com.android.chrome",
        "org.telegram.messenger", "org.telegram.messenger.web", "org.telegram.messenger.beta",
        "org.telegram.plus", "com.exteragram.messenger", "com.radolyn.ayugram",
        "uz.unnarsx.cherrygram", "xyz.nextalone.nagram", "nu.gpu.nagram",
        "com.xtaolabs.pagergram", "fork.risin42.nagramx",
    )
    fun applications(lines: Sequence<String>): List<String> = lines.map(String::trim)
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .distinct().toList()

    fun applicationPackage(scopePackage: String): String = if (scopePackage == "system") "android" else scopePackage

    fun isSystemApp(flags: Int): Boolean =
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    internal val applicationOrder = compareByDescending<ScopeApplication> { it.sectionKey != null }
        .thenByDescending { it.hasSettings }
        .thenBy(String.CASE_INSENSITIVE_ORDER) { it.label }
        .thenBy { it.packageName }
}
