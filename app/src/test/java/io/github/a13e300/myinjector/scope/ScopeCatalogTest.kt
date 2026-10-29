package io.github.a13e300.myinjector.scope

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScopeCatalogTest {
    @Test fun listsAllScopeAppsWithoutRestrictingToSettingsApps() {
        val apps = ScopeCatalog.applications(sequenceOf("system", "com.android.systemui", "com.miui.home",
            "com.termux", "com.android.chrome", "com.termux", "", "# comment"))
        assertEquals(listOf("system", "com.android.systemui", "com.miui.home", "com.termux", "com.android.chrome"), apps)
        assertFalse("com.termux" in ScopeCatalog.settingsApps)
        assertTrue("com.android.chrome" in ScopeCatalog.settingsApps)
        assertEquals("system", ScopeCatalog.components["system"])
    }
    @Test fun packagedListIncludesEverySettingsEntryAndNewTelegramVariants() {
        val scope = File("src/main/resources/META-INF/xposed/scope.list").readLines().toSet()
        assertTrue(scope.containsAll(ScopeCatalog.settingsApps))
        assertTrue(scope.containsAll(ScopeCatalog.components.values))
        assertFalse("com.telegram.plus" in scope)
    }
    @Test fun classifiesSystemAndUpdatedSystemAppsFromApplicationInfo() {
        assertTrue(ScopeCatalog.isSystemApp(android.content.pm.ApplicationInfo.FLAG_SYSTEM))
        assertTrue(ScopeCatalog.isSystemApp(android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        assertFalse(ScopeCatalog.isSystemApp(android.content.pm.ApplicationInfo.FLAG_INSTALLED))
        assertEquals("android", ScopeCatalog.applicationPackage("system"))
        assertEquals("com.android.settings", ScopeCatalog.applicationPackage("com.android.settings"))
    }

    @Test fun prioritizesModuleSettingsThenInjectedSettingsWithinBothGroups() {
        val apps = listOf(
            ScopeApplication("com.android.settings", "A System Settings", true),
            ScopeApplication("com.termux", "A Termux", false),
            ScopeApplication("org.telegram.messenger", "Z Telegram", true),
            ScopeApplication("com.android.chrome", "Z Chrome", false),
            ScopeApplication("com.miui.home", "Z Home", true),
            ScopeApplication("system", "Y Framework", true),
            ScopeApplication("com.android.systemui", "X System UI", true),
        ).sortedWith(ScopeCatalog.applicationOrder)
        assertEquals(listOf("com.android.systemui", "system", "com.miui.home", "org.telegram.messenger", "com.android.settings"),
            apps.filter { it.isSystemApp }.map { it.packageName })
        assertEquals(listOf("com.android.chrome", "com.termux"), apps.filterNot { it.isSystemApp }.map { it.packageName })
        assertEquals("systemui", apps.first().sectionKey)
        assertNull(apps.first { it.packageName == "com.android.settings" }.sectionKey)
        assertTrue(apps.first { it.packageName == "org.telegram.messenger" }.hasSettings)
        assertEquals(3, apps.count { it.sectionKey != null })
    }
}
