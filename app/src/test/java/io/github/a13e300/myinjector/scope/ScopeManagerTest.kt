package io.github.a13e300.myinjector.scope

import org.junit.Assert.*
import org.junit.Test

class ScopeManagerTest {
    private fun manager() = ScopeManager({ it() }, { it() })
    private class Backend : ScopeBackend {
        var enabled = setOf("system")
        var queryFails = false
        var removeFails = false
        var requests = 0
        lateinit var completion: (Boolean, String?) -> Unit
        override fun getScope(): Set<String> { check(!queryFails); return enabled }
        override fun request(packageName: String, result: (Boolean, String?) -> Unit) {
            requests++
            completion = result
        }
        override fun remove(packageName: String) { check(!removeFails); enabled = enabled - packageName }
    }
    @Test fun authorizationIsConfirmedByQueryAndRequestsDoNotSupersedeEachOther() {
        val manager = manager()
        val backend = Backend()
        manager.bindBackend(backend)
        assertTrue(manager.state.ready)
        manager.setEnabled("target", true)
        assertFalse("target" in manager.state.enabled)
        assertEquals(setOf("target"), manager.state.pending)
        manager.setEnabled("other", true)
        assertEquals(1, backend.requests)
        backend.enabled += "target"
        backend.completion(true, null)
        assertTrue("target" in manager.state.enabled)
        assertTrue(manager.state.pending.isEmpty())
        manager.setEnabled("target", false)
        assertFalse("target" in manager.state.enabled)
    }
    @Test fun denialRemovalFailureAndQueryFailureNeverPretendSuccess() {
        val manager = manager()
        val backend = Backend()
        manager.bindBackend(backend)
        manager.setEnabled("target", true)
        backend.completion(false, "denied")
        assertFalse("target" in manager.state.enabled)
        assertTrue(manager.state.message.contains("denied"))
        backend.removeFails = true
        manager.setEnabled("system", false)
        assertTrue("system" in manager.state.enabled)
        assertTrue(manager.state.message.contains("失败"))
        backend.queryFails = true
        manager.refresh()
        assertFalse(manager.state.ready)
    }
    @Test fun oldServiceCallbackDoesNotOverwriteReplacementAndResumeRefreshesExternalChanges() {
        val manager = manager()
        val old = Backend()
        manager.bindBackend(old)
        manager.setEnabled("old", true)
        val replacement = Backend().apply { enabled = setOf("new") }
        manager.bindBackend(replacement)
        old.completion(true, null)
        assertEquals(setOf("new"), manager.state.enabled)
        assertTrue(manager.state.pending.isEmpty())
        replacement.enabled = emptySet()
        manager.refresh()
        assertTrue(manager.state.enabled.isEmpty())
        manager.bindBackend(null)
        assertFalse(manager.state.ready)
    }
    @Test fun queryAfterApprovalIsAuthoritative() {
        val manager = manager()
        val backend = Backend()
        manager.bindBackend(backend)
        manager.setEnabled("target", true)
        backend.completion(true, null)
        assertFalse("target" in manager.state.enabled)
    }
}
