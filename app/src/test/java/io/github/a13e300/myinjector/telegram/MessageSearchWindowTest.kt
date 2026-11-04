package io.github.a13e300.myinjector.telegram

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSearchWindowTest {
    // The server returns the selected media type, including other senders.
    private fun search(
        source: Map<Long, List<Int>>,
        anchor: SearchMessageKey,
        matches: Set<SearchMessageKey>,
    ) {
        val peers = source.keys.toList()
        val window = MessageSearchWindow(peers, anchor)
        val visible = mutableSetOf<SearchMessageKey>()
        var first = true
        var requests = 0
        var emptyPages = 0
        fun fetch(start: SearchPage) {
            var page = start
            while (true) {
                assertTrue("Search did not finish", ++requests < 1000)
                val history = source.getValue(peers[page.peer])
                val offset = if (page.offset == 0) 0 else history.count { it >= page.offset }
                val raw = history.drop((offset + page.addOffset).coerceAtLeast(0)).take(40)
                val added = window.add(page, raw).filter { it in matches }
                visible.addAll(added)
                if (added.isNotEmpty()) break
                emptyPages++
                page = window.nextAfterEmpty(page, first) ?: break
            }
            first = false
        }
        fetch(window.initial)
        for (direction in listOf(-1, 1)) {
            while (true) fetch(window.next(direction) ?: break)
        }
        assertEquals(matches, visible)
        assertTrue(window.newerEnd && window.olderEnd)
        assertTrue("Fixture must include pages without matching senders", emptyPages > 2)
    }

    @Test
    fun sparseSenderAcrossBothDirections() {
        val source = mapOf(-100L to (1..3000).reversed().toList())
        val matches = source.getValue(-100L).filter { it % 237 == 0 }.map { SearchMessageKey(-100, it) }.toSet()
        search(source, SearchMessageKey(-100, 1541), matches)
    }

    @Test
    fun onlyNewerResultsMatch() {
        search(mapOf(-100L to (1..1000).reversed().toList()), SearchMessageKey(-100, 300),
            setOf(SearchMessageKey(-100, 900)))
    }

    @Test
    fun migratedPeersHaveIndependentCursors() {
        val source = mapOf(-100L to (1..500).reversed().toList(), -10L to (1..500).reversed().toList())
        search(source, SearchMessageKey(-100, 350), setOf(SearchMessageKey(-10, 100), SearchMessageKey(-10, 400)))
    }

    @Test
    fun noMatchingSenderFinishesBothDirections() {
        search(mapOf(-100L to (1..600).reversed().toList()), SearchMessageKey(-100, 300), emptySet())
    }
}
