package io.github.a13e300.myinjector.scope

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class ScopeCatalogTest {
    @Test fun listsScopeAppsWithoutRestrictingToSettingsAppsAndExcludesSystemPages() {
        val apps = ScopeCatalog.applications(sequenceOf("system", "com.android.systemui", "com.miui.home",
            "com.termux", "com.android.chrome", "com.termux", "", "# comment"))
        assertEquals(listOf("com.termux", "com.android.chrome"), apps)
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
}
