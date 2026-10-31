package io.github.a13e300.myinjector.ui.settings

import org.junit.Assert.*
import org.junit.Test

class SettingsCatalogTest {
    @Test fun preservesExistingStorageKeysAndDefaults() {
        val all = moduleSettingsSections().flatMap { it.items }
        val expected = setOf(
            "checkHotUpdate", "noWakePath", "noMiuiIntent", "clipboardWhitelist", "clipboardWhitelistPackages",
            "fixSync", "xSpace", "forceNewTask", "forceNewTaskRules", "forceNewTaskDebug", "overrideStatusBar",
            "overrideStatusBarRules", "noSwipeToKillLockedProcess", "noSwipeToKillNoRestrictProcess",
            "bypassShellDexOptRestriction", "fixHyperOSAvoidMoveToFront", "showNotificationDetail",
            "noDndNotification", "alwaysExpandNotification", "fixWhiteSplash", "loopNotificationControlCenter",
            "topPullSwitchNotificationControlCenter",
            "marketRemoveSplashAd",
            "marketSuperMinimalMode",
            "marketSimplifyUpdates",
            "miuiHomeOpenAospSettings", "miuiHomeDragKill", "miuiHomeDisablePreLaunch",
        )
        assertEquals(expected, all.map { it.key }.toSet())
        assertEquals(expected.size, all.size)
        assertTrue(all.filterIsInstance<SettingsItemSpec.Switch>().all {
            it.defaultValue == (it.sectionKey == "market")
        })
        assertTrue(all.filterIsInstance<SettingsItemSpec.Text>().filter { it.key.endsWith("Rules") }
            .all { !it.helpText.isNullOrBlank() })
        assertEquals(listOf("system", "systemui", "market", "miuihome"), moduleSettingsSections().drop(1).map { it.key })
    }
}
