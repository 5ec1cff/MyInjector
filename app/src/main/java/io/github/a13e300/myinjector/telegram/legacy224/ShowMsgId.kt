package io.github.a13e300.myinjector.telegram.legacy224

import android.text.NoCopySpan
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.hookAfter
import io.github.a13e300.myinjector.logE
import java.lang.reflect.Modifier
import kotlin.math.ceil

/**
 * Mapping verified against exteraGram-full-universal-20261004.apk, SHA-256
 * 1ed01526c0cb57bc798c29cc06ab4d01d30282ce2cc1b177f0222e906c22581f.
 * Unlike the official client, currentTimeString is a SpannableStringBuilder and
 * chat_timePaint is com.exteragram.messenger.utils.ui.TextPaint (an Android subclass).
 * Do not apply the official client's obfuscated field/DEX-instruction assumptions here.
 */
class ShowMsgId : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.showMsgId

    // A marker, not a textual prefix check: a legitimate timestamp/author can start with digits.
    private class InjectedId : NoCopySpan
    private var reportedFailure = false

    override fun onHook() {
        // Resolve the actual APK's retained names and validate every member's type before
        // installing the hook. Missing/changed schema fails closed through IHook's error log.
        val cellClass = findClass("org.telegram.ui.Cells.ChatMessageCell")
        val messageClass = findClass("org.telegram.messenger.MessageObject")
        val ownerClass = findClass("org.telegram.tgnet.TLRPC\$Message")
        val themeClass = findClass("org.telegram.ui.ActionBar.Theme")
        val measureTime = cellClass.getDeclaredMethod("measureTime", messageClass).apply {
            require(returnType == Void.TYPE && !Modifier.isStatic(modifiers))
            isAccessible = true
        }
        val currentTime = cellClass.getDeclaredField("currentTimeString").apply {
            require(type == SpannableStringBuilder::class.java)
            isAccessible = true
        }
        val timeWidth = cellClass.getDeclaredField("timeWidth").apply {
            require(type == Int::class.javaPrimitiveType)
            isAccessible = true
        }
        val timeTextWidth = cellClass.getDeclaredField("timeTextWidth").apply {
            require(type == Int::class.javaPrimitiveType)
            isAccessible = true
        }
        val isMegagroup = cellClass.getDeclaredField("isMegagroup").apply {
            require(type == Boolean::class.javaPrimitiveType)
            isAccessible = true
        }
        val timePaint = themeClass.getDeclaredField("chat_timePaint").apply {
            require(Modifier.isStatic(modifiers) && TextPaint::class.java.isAssignableFrom(type))
            isAccessible = true
        }
        val messageOwner = messageClass.getDeclaredField("messageOwner").apply {
            require(type == ownerClass)
            isAccessible = true
        }
        val idField = ownerClass.getDeclaredField("id").apply {
            require(type == Int::class.javaPrimitiveType)
            isAccessible = true
        }
        val realIdField = ownerClass.getDeclaredField("realId").apply {
            require(type == Int::class.javaPrimitiveType)
            isAccessible = true
        }
        val postAuthorField = ownerClass.getDeclaredField("post_author").apply {
            require(type == String::class.java)
            isAccessible = true
        }
        val fromChatId = messageClass.getDeclaredMethod("getFromChatId").apply {
            require(returnType == Long::class.javaPrimitiveType)
            isAccessible = true
        }
        val dialogId = messageClass.getDeclaredMethod("getDialogId").apply {
            require(returnType == Long::class.javaPrimitiveType)
            isAccessible = true
        }

        measureTime.hookAfter(cond = ::isEnabled) { param ->
            // Never replace the original failure or operate on an incomplete measurement.
            if (param.throwable == null) {
                runCatching {
                    val cell = param.thisObject ?: return@runCatching
                    val message = param.args.firstOrNull() ?: return@runCatching
                    val owner = messageOwner.get(message) ?: return@runCatching
                    val original = currentTime.get(cell) as? SpannableStringBuilder
                        ?: return@runCatching
                    if (original.getSpans(0, original.length, InjectedId::class.java).isNotEmpty()) {
                        return@runCatching
                    }
                    val paint = timePaint.get(null) as? TextPaint ?: return@runCatching
                    val realId = realIdField.getInt(owner)
                    val id = if (realId == 0) idField.getInt(owner) else realId
                    val prefix = StringBuilder().append(id).append(' ')
                    // The APK deliberately suppresses this signature in measureTime for
                    // channel-origin posts in megagroups (21388-21392 of the decompiled cell).
                    // Restore it only there; other author signatures are already in original.
                    if (isMegagroup.getBoolean(cell) &&
                        fromChatId.invoke(message) == dialogId.invoke(message)
                    ) {
                        val author = postAuthorField.get(owner) as? String
                        val singleLine = author?.replace("\n", "")?.replace("\r", "")
                        if (!singleLine.isNullOrEmpty()) prefix.append(singleLine).append(' ')
                    }
                    // Preserve edited-icon/TON/star and author spans. Assigning a String to
                    // this APK's builder field would fail; string concatenation loses spans.
                    val result = SpannableStringBuilder(prefix).append(original)
                    result.setSpan(InjectedId(), 0, prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    // Match the APK's ceil(measureText(CharSequence, start, end)) convention.
                    // Only add the text delta: retain views/replies/pin/effect icon allowances.
                    val delta = ceil(paint.measureText(result, 0, result.length).toDouble()).toInt() -
                        ceil(paint.measureText(original, 0, original.length).toDouble()).toInt()
                    val newTextWidth = Math.addExact(timeTextWidth.getInt(cell), delta)
                    val newWidth = Math.addExact(timeWidth.getInt(cell), delta)
                    currentTime.set(cell, result)
                    timeTextWidth.setInt(cell, newTextWidth)
                    timeWidth.setInt(cell, newWidth)
                    // measureTime recreates the original builder and both widths on EVERY
                    // invocation (21479, 21575-21580). Recycling/repeated measurement therefore
                    // starts clean, including after disabling this hook's runtime condition.
                }.onFailure {
                    if (!reportedFailure) {
                        reportedFailure = true
                        logE("ShowMsgId: timestamp update failed", it)
                    }
                }
            }
        }
    }
}
