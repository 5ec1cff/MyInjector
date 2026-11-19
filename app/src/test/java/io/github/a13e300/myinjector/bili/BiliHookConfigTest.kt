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
            assertEquals(BiliFeature.entries.size, json.length())
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
}
