package io.github.a13e300.myinjector.telegram

internal data class SearchMessageKey(val dialog: Long, val id: Int)
internal data class SearchPage(val direction: Int, val peer: Int, val offset: Int) {
    val addOffset: Int get() = if (direction == 0) -20 else if (direction < 0) -40 else 0
}

// Message IDs are only ordered inside one peer. A migrated group's old peer
// follows its new peer, even when their message IDs overlap.
internal class MessageSearchWindow(val peers: List<Long>, anchor: SearchMessageKey) {
    private var newerPeer = peers.indexOf(anchor.dialog).coerceAtLeast(0)
    private var olderPeer = newerPeer
    val initial = SearchPage(0, newerPeer, anchor.id)
    val rows = ArrayList<SearchMessageKey>()
    var olderEnd = false
        private set
    var newerEnd = false
        private set

    fun next(direction: Int): SearchPage? {
        if (direction > 0 && olderEnd || direction < 0 && newerEnd) return null
        val peer = if (direction > 0) olderPeer else newerPeer
        val edge = if (direction > 0) rows.lastOrNull { it.dialog == peers[peer] }
            else rows.firstOrNull { it.dialog == peers[peer] }
        return SearchPage(direction, peer, edge?.id ?: if (direction < 0) 1 else 0)
    }

    // A page without matching senders can still move the server cursor.
    fun nextAfterEmpty(page: SearchPage, first: Boolean): SearchPage? {
        val direction = if (page.direction != 0) page.direction else if (initial.offset == 1) -1 else 1
        return next(direction) ?: if (first) next(-direction) else null
    }

    fun add(page: SearchPage, ids: List<Int>): List<SearchMessageKey> {
        val existing = rows.toHashSet()
        val added = ids.filter { id -> id > 0 && (page.direction == 0 || page.offset == 0 ||
            if (page.direction > 0) id < page.offset else id >= page.offset)
        }.map { SearchMessageKey(peers[page.peer], it) }.distinct().filter { it !in existing }
        rows.addAll(added)
        rows.sortWith(compareBy<SearchMessageKey> { peers.indexOf(it.dialog) }.thenByDescending { it.id })
        if (page.direction != 0 && added.isEmpty()) {
            if (page.direction > 0) {
                if (page.peer < peers.lastIndex) olderPeer++ else olderEnd = true
            } else {
                if (page.peer > 0) newerPeer-- else newerEnd = true
            }
        }
        return added
    }
}
