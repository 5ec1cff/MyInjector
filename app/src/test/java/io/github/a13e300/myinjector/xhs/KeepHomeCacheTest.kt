package io.github.a13e300.myinjector.xhs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepHomeCacheTest {
    @Test fun keepsRecommendCacheAtColdStart() {
        assertTrue(shouldKeepXhsHomeCache("homefeed_recommend", coldStart = true, hasCachedNotes = true))
    }

    @Test fun allowsNetworkWhenThereAreNoCachedNotes() {
        assertFalse(shouldKeepXhsHomeCache("homefeed_recommend", coldStart = true, hasCachedNotes = false))
    }

    @Test fun preservesManualRefreshAndOtherChannels() {
        assertFalse(shouldKeepXhsHomeCache("homefeed_recommend", coldStart = false, hasCachedNotes = true))
        assertFalse(shouldKeepXhsHomeCache("homefeed.local.v2.nearby", coldStart = true, hasCachedNotes = true))
        assertFalse(shouldKeepXhsHomeCache(null, coldStart = true, hasCachedNotes = true))
    }

    @Test fun oldSettingsEnableFeatureAndPersistOptOut() {
        assertTrue(XhsHookConfig.readFromJson(JSONObject()).keepHomeCache)
        val settings = XhsHookConfig(keepHomeCache = false)
        val json = JSONObject().apply { settings.writeToJson(this) }
        assertEquals(settings, XhsHookConfig.readFromJson(json))
    }
}
