package io.github.a13e300.myinjector.xhs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HideHomeAdsTest {
    @Test fun recognizesEachHostAdFlag() {
        assertTrue(isXhsHomeAd(true, "note", ""))
        assertTrue(isXhsHomeAd(false, "advertisement", ""))
        assertTrue(isXhsHomeAd(false, "note", "广告"))
        assertTrue(isXhsHomeAd(false, "ads", "", showAdTips = true))
    }

    @Test fun keepsNormalNotesWithEmptyAdInfo() {
        assertFalse(isXhsHomeAd(false, "note", ""))
        assertFalse(isXhsHomeAd(false, "video", null))
        assertFalse(isXhsHomeAd(false, "live", ""))
        assertFalse(isXhsHomeAd(false, null, null))
    }

    @Test fun oldSettingsEnableFeatureAndPersistOptOut() {
        assertTrue(XhsHookConfig.readFromJson(JSONObject()).hideHomeAds)
        val settings = XhsHookConfig(hideHomeAds = false)
        val json = JSONObject().apply { settings.writeToJson(this) }
        assertEquals(settings, XhsHookConfig.readFromJson(json))
    }
}
