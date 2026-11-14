package io.github.a13e300.myinjector.system_server

import org.junit.Assert.assertEquals
import org.junit.Test

class XSpaceWhitelistTest {
    private val shell = "com.android.shell"
    private val xmsf = "com.xiaomi.xmsf"

    @Test fun disabledAtStartupKeepsSystemDefaults() {
        val packages = mutableListOf("android", shell)
        val actions = mutableListOf("android.intent.action.SEND")
        XSpaceWhitelist(packages, actions).setEnabled(false)
        assertEquals(listOf("android", shell), packages)
        assertEquals(listOf("android.intent.action.SEND"), actions)
    }

    @Test fun repeatedEnableDoesNotAddDuplicatesOrLoseSavedActions() {
        val packages = mutableListOf("android", shell)
        val actions = mutableListOf("android.intent.action.SEND", "android.intent.action.SENDTO")
        val originalActions = actions.toList()
        val whitelist = XSpaceWhitelist(packages, actions)
        repeat(2) { whitelist.setEnabled(true) }
        assertEquals(listOf("android", shell, xmsf), packages)
        assertEquals(emptyList<String>(), actions)
        repeat(2) { whitelist.setEnabled(false) }
        assertEquals(listOf("android", shell), packages)
        assertEquals(originalActions, actions)
    }

    @Test fun keepsBothPackagesWhenTheyAlreadyBelongToSystem() {
        val packages = mutableListOf("android", shell, xmsf)
        val actions = mutableListOf<String>()
        val whitelist = XSpaceWhitelist(packages, actions)
        whitelist.setEnabled(true)
        whitelist.setEnabled(false)
        assertEquals(listOf("android", shell, xmsf), packages)
        assertEquals(emptyList<String>(), actions)
    }

    @Test fun unloadAndReloadRestoreTheSameStateAcrossCycles() {
        val packages = mutableListOf("android")
        val actions = mutableListOf("android.intent.action.SEND")
        repeat(3) {
            val whitelist = XSpaceWhitelist(packages, actions)
            whitelist.setEnabled(true)
            assertEquals(listOf("android", shell, xmsf), packages)
            assertEquals(emptyList<String>(), actions)
            whitelist.setEnabled(false)
            assertEquals(listOf("android"), packages)
            assertEquals(listOf("android.intent.action.SEND"), actions)
        }
    }
}
