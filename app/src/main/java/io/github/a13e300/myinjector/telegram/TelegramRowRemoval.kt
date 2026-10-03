package io.github.a13e300.myinjector.telegram

// Keep range boundaries (e.g. participantsEndRow) valid even when they equal
// a removed cell's index. Only the explicitly removed fields become -1.
internal class TelegramRowRemoval(count: Int, positions: Collection<Int>) {
    private val removed = positions.filter { it in 0 until count }.distinct().sorted()
    val count = count - removed.size
    fun shift(index: Int): Int = if (index < 0) index else index - removed.count { it < index }
}
