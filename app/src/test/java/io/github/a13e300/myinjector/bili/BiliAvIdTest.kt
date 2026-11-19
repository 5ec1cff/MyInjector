package io.github.a13e300.myinjector.bili

import org.junit.Assert.*
import org.junit.Test

class BiliAvIdTest {
    private val first = BiliVideoId(170001, "BV17x411w7KC")
    private val second = BiliVideoId(455017605, "BV1Q541167Qg")

    @Test
    fun changesOnlyCurrentVideoIdAndKeepsOuterRenderAfterNestedRender() {
        val scope = BiliAvHeadlineScope { it as BiliVideoId }
        assertEquals(first.bvid, scope.text(first.bvid))
        scope.enter(first)
        assertEquals("AV170001", scope.text(first.bvid))
        assertEquals(second.bvid, scope.text(second.bvid))
        assertEquals("AV170001", scope.text("AV170001"))
        scope.enter(second)
        assertEquals("AV455017605", scope.text(second.bvid))
        assertEquals(first.bvid, scope.text(first.bvid))
        scope.leave()
        assertEquals("AV170001", scope.text(first.bvid))
        scope.leave()
        assertEquals(first.bvid, scope.text(first.bvid))
    }

    @Test
    fun failedReadDoesNotUseOuterVideoAndCleanupRestoresOuterRender() {
        val scope = BiliAvHeadlineScope { (it as? BiliVideoId) ?: error("missing video") }
        scope.enter(first)
        assertThrows(IllegalStateException::class.java) { scope.enter(Unit) }
        assertEquals(first.bvid, scope.text(first.bvid))
        scope.leave()
        assertEquals("AV170001", scope.text(first.bvid))
        scope.leave()
    }

    @Test
    fun missingIdKeepsOriginalText() {
        val scope = BiliAvHeadlineScope { it as BiliVideoId }
        scope.enter(first.copy(aid = 0))
        assertEquals(first.bvid, scope.text(first.bvid))
        assertNull(scope.text(null))
        scope.leave()
        scope.enter(first.copy(bvid = ""))
        assertEquals("", scope.text(""))
        scope.leave()
    }

    @Test
    fun separateThreadsDoNotShareVideo() {
        val scope = BiliAvHeadlineScope { it as BiliVideoId }
        scope.enter(first)
        var fromOther: Any? = null
        val worker = Thread { fromOther = scope.text(first.bvid) }
        worker.start()
        worker.join()
        assertEquals(first.bvid, fromOther)
        assertEquals("AV170001", scope.text(first.bvid))
        scope.leave()
    }
}
