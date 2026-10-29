package io.github.a13e300.myinjector.scope

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
        .filter { it.isNotEmpty() && !it.startsWith("#") && it !in components.values }
        .distinct().toList()
}
