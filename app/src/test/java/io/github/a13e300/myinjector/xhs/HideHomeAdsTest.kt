package io.github.a13e300.myinjector.xhs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HideHomeAdsTest {
    @Test fun countsDisplayFilteringWithoutCountingStorageAgain() {
        val counter = HomeAdCounter()
        val notes = listOf("note", "ad", "note2", "ad")
        val stored = counter.filter(notes, countFiltered = false) { it == "ad" }
        assertEquals(0L, counter.count)
        val displayed = counter.filter(notes) { it == "ad" }
        assertEquals(stored, displayed)
        assertEquals(listOf("note", "note2"), displayed)
        assertEquals(2L, counter.count)
        assertEquals(4, notes.size)
    }

    @Test fun cachedFilteringIsNotCountedAgainDuringConversion() {
        val counter = HomeAdCounter()
        val cached = counter.filter(listOf("ad", "note")) { it == "ad" }
        counter.filter(cached) { it == "ad" }
        assertEquals(1L, counter.count)
        counter.filter(listOf("ad")) { it == "ad" }
        assertEquals(2L, counter.count)
        assertEquals(0L, HomeAdCounter().count)
    }

    @Test fun accumulatesFiltersFromDifferentThreads() {
        val counter = HomeAdCounter()
        val executor = Executors.newFixedThreadPool(4)
        try {
            val tasks = (0 until 4).map {
                executor.submit { repeat(1000) { counter.filter(listOf("ad")) { true } } }
            }
            tasks.forEach { it.get(5, TimeUnit.SECONDS) }
            assertEquals(4000L, counter.count)
        } finally {
            executor.shutdownNow()
        }
    }

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
