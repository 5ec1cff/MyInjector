package io.github.a13e300.myinjector.bili

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class BiliInterfaceStateTest {
    @Test
    fun savedChoiceDoesNotChangeActiveModeOrInventObservation() {
        val state = BiliInterfaceState(BiliHookConfig())
        val component = BiliInterfaceComponent.SEARCH
        val summary = state.summary(component, BiliInterfaceMode.NEW)
        assertEquals(BiliInterfaceMode.FOLLOW, state.activeModes.getValue(component))
        assertTrue(summary.contains("待重启"))
        assertTrue(summary.contains("实际页面：未观测；入口选择：未观测"))
    }

    @Test
    fun actualPageCanDifferFromSelectedGateAndCanUseBothVersions() {
        val component = BiliInterfaceComponent.OPUS
        val state = BiliInterfaceState(BiliHookConfig().withInterfaceMode(component, BiliInterfaceMode.NEW))
        state.selected(component, "gate", true)
        state.displayed(component, false)
        assertTrue(state.summary(component, BiliInterfaceMode.NEW).contains("实际页面：旧版；入口选择：新版"))
        state.displayed(component, true)
        assertTrue(state.summary(component, BiliInterfaceMode.NEW).contains("实际页面：新版、旧版均已观测"))
        assertTrue(state.summary(BiliInterfaceComponent.SEARCH, BiliInterfaceMode.FOLLOW).contains("实际页面：未观测"))
    }

    @Test
    fun componentErrorsRemainIndependent() {
        val state = BiliInterfaceState(BiliHookConfig())
        state.failed(BiliInterfaceComponent.MINE)
        assertTrue(state.hasError(BiliInterfaceComponent.MINE))
        assertFalse(state.hasError(BiliInterfaceComponent.SEARCH))
    }
}
