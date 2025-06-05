package io.github.a13e300.myinjector.telegram

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

fun String.firstUnicodeChar(): Int {
    val c = get(0).code
    if (c >= 0xd800 && c <= 0xdfff) {
        if (c < 0xdc00 && length >= 2) {
            val d = get(1).code
            if (d >= 0xdc00 && d <= 0xdfff) {
                return 0x010000.or((c - 0xd800).shl(10)).or(d - 0xdc00)
            }
        }
    }
    return c
}

// https://github.com/DrKLO/Telegram/blob/17067dfc6a1f69618a006b14e1741b75c64b276a/TMessagesProj/src/main/java/org/telegram/messenger/utils/CustomHtml.java#L248
fun String.toHtml(): String = StringBuilder().apply {
    for (ch in this@toHtml) {
        when (ch) {
            ' ' -> append("&nbsp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '&' -> append("&amp;")
            '\n' -> append("<br>")
            else -> append(ch)
        }
    }
}.toString()

fun Context.findBaseActivity(): Activity =
    this as? Activity
        ?: (this as? ContextWrapper)?.baseContext?.findBaseActivity()
        ?: error("not activity: $this")

