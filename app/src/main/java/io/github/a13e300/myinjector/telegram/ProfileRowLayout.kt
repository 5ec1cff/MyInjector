package io.github.a13e300.myinjector.telegram

// Native row IDs and half-open interval boundaries must move together. Always
// construct this from a fresh updateRowsIds result, including after disabling rows.
internal class ProfileRowLayout(originalCount: Int, anchor: Int, requestedRows: Int) {
    val extraCount = if (originalCount > 0) requestedRows else 0
    val count = originalCount + extraCount
    val position = if (anchor in 0 until originalCount) anchor else minOf(1, maxOf(0, originalCount - 1))

    fun shift(index: Int): Int = if (index >= position) index + extraCount else index
}
