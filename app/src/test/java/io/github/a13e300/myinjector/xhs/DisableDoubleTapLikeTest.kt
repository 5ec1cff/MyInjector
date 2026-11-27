package io.github.a13e300.myinjector.xhs

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisableDoubleTapLikeTest {
    private enum class NoteClick { SINGLE_CLICK, DOUBLE_CLICK, MULTI_CLICK, LONG_PRESS, ZOOM_IN, ZOOM_OUT }
    private enum class OtherClick { DOUBLE_CLICK, MULTI_CLICK }
    private val clickTypes: Set<Class<*>> = setOf(NoteClick::class.java)

    @Test fun blocksNoteDoubleAndMultipleClicks() {
        assertTrue(isXhsDoubleTapLike(NoteClick.DOUBLE_CLICK, clickTypes))
        assertTrue(isXhsDoubleTapLike(NoteClick.MULTI_CLICK, clickTypes))
    }

    @Test fun preservesOtherGesturesAndEvents() {
        for (event in listOf(NoteClick.SINGLE_CLICK, NoteClick.LONG_PRESS, NoteClick.ZOOM_IN, NoteClick.ZOOM_OUT,
            OtherClick.DOUBLE_CLICK, OtherClick.MULTI_CLICK, null, "DOUBLE_CLICK", Any())) {
            assertFalse("Unexpectedly blocked $event", isXhsDoubleTapLike(event, clickTypes))
        }
    }

    @Test fun enablesForExistingSettingsAndPersistsOptOut() {
        assertTrue(XhsHookConfig().disableDoubleTapLike)
        assertTrue(XhsHookConfig.readFromJson(JSONObject()).disableDoubleTapLike)
        val settings = XhsHookConfig(disableDoubleTapLike = false, betterShare = false)
        val json = JSONObject().apply { settings.writeToJson(this) }
        assertEquals(settings, XhsHookConfig.readFromJson(json))
    }
}
