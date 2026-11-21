package io.github.a13e300.myinjector.bili

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiliHookConfigTest {
    @Test
    fun defaultsKeepExistingFeaturesEnabled() {
        val config = BiliHookConfig.readFromJson(JSONObject())
        assertEquals(BiliFeature.entries.toSet(), config.enabledFeatures)
        assertEquals(BiliFeature.entries.size, BiliFeature.entries.map { it.key }.toSet().size)
    }

    @Test
    fun everySwitchPersistsIndependently() {
        BiliFeature.entries.forEach { disabled ->
            val config = BiliHookConfig().withFeature(disabled, false)
            val json = JSONObject(config.toJson().toString())
            assertEquals(BiliFeature.entries.size + 1, json.length())
            assertEquals(config, BiliHookConfig.readFromJson(json))
            BiliFeature.entries.forEach { feature ->
                assertEquals(feature != disabled, json.getBoolean(feature.key))
            }
            assertEquals(BiliHookConfig(), config.withFeature(disabled, true))
        }
    }

    @Test
    fun invalidAndFutureKeysDoNotDisableOtherFeatures() {
        val json = JSONObject()
            .put("viewSubtitles", false)
            .put("viewCover", JSONObject.NULL)
            .put("removeSplashAd", 123)
            .put("futureFeature", false)
        val config = BiliHookConfig.readFromJson(json)
        assertFalse(config.isEnabled(BiliFeature.VIEW_SUBTITLES))
        assertTrue(config.isEnabled(BiliFeature.VIEW_COVER))
        assertTrue(config.isEnabled(BiliFeature.REMOVE_SPLASH_AD))
        assertEquals(BiliFeature.entries.size - 1, config.enabledFeatures.size)
    }

    @Test
    fun oldShareSwitchMigratesAndExplicitModeTakesPriority() {
        assertEquals(BiliInterfaceMode.NEW, BiliHookConfig().interfaceMode(BiliInterfaceComponent.SHARE))
        val legacy = JSONObject().put("forceNewShare", false)
        assertEquals(BiliInterfaceMode.FOLLOW, BiliHookConfig.readFromJson(legacy).interfaceMode(BiliInterfaceComponent.SHARE))
        legacy.put("forceNewShare", true)
        assertEquals(BiliInterfaceMode.NEW, BiliHookConfig.readFromJson(legacy).interfaceMode(BiliInterfaceComponent.SHARE))
        legacy.put("interfaceVersions", JSONObject().put("share", "old"))
        val config = BiliHookConfig.readFromJson(legacy)
        assertEquals(BiliInterfaceMode.OLD, config.interfaceMode(BiliInterfaceComponent.SHARE))
        assertFalse(config.toJson().has("forceNewShare"))
    }

    @Test
    fun interfaceModesPersistIndependently() {
        BiliInterfaceComponent.entries.forEach { component ->
            BiliInterfaceMode.entries.forEach { mode ->
                val changed = BiliHookConfig().withInterfaceMode(component, mode)
                assertEquals(changed, BiliHookConfig.readFromJson(JSONObject(changed.toJson().toString())))
                BiliInterfaceComponent.entries.filter { it != component }.forEach {
                    assertEquals(it.defaultMode, changed.interfaceMode(it))
                }
                assertEquals(BiliFeature.entries.toSet(), changed.enabledFeatures)
            }
        }
    }

    @Test
    fun invalidInterfaceModesKeepDefaultsAndOtherChoices() {
        val config = BiliHookConfig.readFromJson(JSONObject().put("interfaceVersions", JSONObject()
            .put("share", "unknown").put("search", true).put("opus", JSONObject.NULL)
            .put("history", "old").put("futureComponent", "new")))
        assertEquals(BiliInterfaceMode.NEW, config.interfaceMode(BiliInterfaceComponent.SHARE))
        assertEquals(BiliInterfaceMode.FOLLOW, config.interfaceMode(BiliInterfaceComponent.SEARCH))
        assertEquals(BiliInterfaceMode.FOLLOW, config.interfaceMode(BiliInterfaceComponent.OPUS))
        assertEquals(BiliInterfaceMode.OLD, config.interfaceMode(BiliInterfaceComponent.HISTORY))
    }
}
