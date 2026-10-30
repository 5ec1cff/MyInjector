package io.github.a13e300.myinjector.config

import org.junit.Assert.*
import org.junit.Test

class HookConfigsTest {
    @Test fun preservesRuleSyntaxAndIgnoresMalformedRows() {
        val config = HookConfigs.system(ConfigValues(mapOf(
            "forceNewTaskRules" to "src/.Source:dst/.Target:ir,nd\nbroken\n*:dst",
            "overrideStatusBarRules" to "pkg/.Screen:light\npkg2:dark\nbad:other",
            "clipboardWhitelistPackages" to "one\ntwo",
        )))
        assertEquals(2, config.forceNewTaskRulesCount)
        val first = config.getForceNewTaskRules(0)
        assertEquals("src.Source", first.sourceComponent)
        assertEquals("dst.Target", first.targetComponent)
        assertTrue(first.ignoreResult)
        assertTrue(first.useNewDocument)
        assertEquals("*", config.getForceNewTaskRules(1).sourcePackage)
        assertEquals(2, config.overrideStatusBarRulesCount)
        assertEquals("pkg.Screen", config.getOverrideStatusBarRules(0).component)
        assertTrue(config.getOverrideStatusBarRules(0).light)
        assertFalse(config.getOverrideStatusBarRules(1).light)
        assertEquals(listOf("one", "two"), config.clipboardWhitelistPackagesList)
    }
    @Test fun preservesDefaultsAndAllSwitches() {
        val defaults = ConfigValues(emptyMap<String, Any>())
        assertFalse(HookConfigs.system(defaults).xSpace)
        assertFalse(HookConfigs.systemUI(defaults).alwaysExpandNotification)
        assertFalse(HookConfigs.systemUI(defaults).loopNotificationControlCenter)
        assertFalse(HookConfigs.systemUI(defaults).topPullSwitchNotificationControlCenter)
        assertFalse(HookConfigs.miuiHome(defaults).disablePreLaunch)
        val all = ConfigValues(SettingsSchema.defaults.mapValues { (_, v) -> if (v is Boolean) true else v })
        assertTrue(HookConfigs.system(all).fixHyperOSAvoidMoveToFromt)
        assertTrue(HookConfigs.system(all).noSwipeToKillLockedProcess)
        assertTrue(HookConfigs.system(all).bypassShellDexOptRestriction)
        assertTrue(HookConfigs.systemUI(all).showNotificationDetail)
        assertTrue(HookConfigs.systemUI(all).loopNotificationControlCenter)
        assertTrue(HookConfigs.systemUI(all).topPullSwitchNotificationControlCenter)
        assertTrue(HookConfigs.miuiHome(all).dragKill)
        assertTrue(HookConfigs.miuiHome(all).openAospSettings)
        assertTrue(HookConfigs.miuiHome(all).disablePreLaunch)
    }
}
