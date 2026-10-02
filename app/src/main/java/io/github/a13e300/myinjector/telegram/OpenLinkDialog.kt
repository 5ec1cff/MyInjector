package io.github.a13e300.myinjector.telegram

import android.app.Dialog
import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.style.StyleSpan
import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.hook
import io.github.a13e300.myinjector.arch.hookBefore
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.enums.UsingType
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Proxy

// 自动修正一些包含了错误字符的链接，在打开时提供 fix 选项以打开修复后的链接
class OpenLinkDialog : MyDynHook("openLinkDialog") {
    data class FixLink(
        val pos: Int,
        val url: String,
        var openRunnable: Runnable?
    )

    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.openLinkDialog

    override fun onHook() {
        val creator = TelegramHandler.creator
        val found by lazy { findOpenLinkDialog(creator.bridge) }
        val members = listOf("external", "alert", "open", "show", "neutralText", "neutralListener", "message")
            .associateWith { key -> creator.create("OpenLinkDialog.$key") {
                val descriptor = found.getValue(key)
                if (key in listOf("neutralText", "neutralListener", "message")) {
                    val f = DexField(descriptor)
                    ObfsInfo(f.className, f.name, descriptor)
                } else {
                    val m = DexMethod(descriptor)
                    ObfsInfo(m.className, m.name, descriptor)
                }
            } }
        fun method(key: String) = DexMethod(members.getValue(key).descriptor)
            .getMethodInstance(classLoader).apply { isAccessible = true }
        fun field(key: String) = DexField(members.getValue(key).descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        val openUrl = method("open")
        val neutralText = field("neutralText")
        val neutralListener = field("neutralListener")
        val messageField = field("message")
        val click = neutralListener.type.methods.single {
            it.parameterCount == 2 && it.parameterTypes[1] == Integer.TYPE
        }
        val fixLink = ThreadLocal<FixLink>()
        val regexTelegraph = Regex("^https?://telegra\\.ph")
        val escapeChars = Regex("[^!#\$&'*+\\(\\),-./:;%=\\?@_~0-9A-Za-z]")
        method("external").hook(cond = ::isEnabled, before = { param ->
            val url = param.args[1] as String
            if (regexTelegraph.find(url) == null) {
                escapeChars.find(url)?.let { match ->
                    fixLink.set(FixLink(match.range.first, url.substring(0, match.range.first), null))
                    param.args[4] = true
                }
            }
        }, after = { fixLink.remove() })
        // Hook the Context implementation, which survives when fragment wrappers are inlined.
        method("alert").hookBefore { param ->
            fixLink.get()?.let { fix ->
                val context = param.args[0] as Context
                val inlineReturn = param.args[6] as Long
                val tryTelegraph = param.args[3]
                val progress = param.args[7]
                fix.openRunnable = Runnable {
                    openUrl.invoke(null, context, Uri.parse(fix.url), inlineReturn == 0L, tryTelegraph, progress)
                }
            }
        }
        method("show").hookBefore { param ->
            val fix = fixLink.get() ?: return@hookBefore
            if (fix.openRunnable == null) return@hookBefore
            fixLink.remove()
            val dialog = param.thisObject as Dialog
            neutralText.set(dialog, "fix")
            neutralListener.set(dialog, Proxy.newProxyInstance(classLoader, arrayOf(neutralListener.type)) { proxy, m, args ->
                when {
                    m == click -> { fix.openRunnable?.run(); dialog.dismiss(); null }
                    m.name == "toString" -> "OpenLinkDialog.fix"
                    m.name == "hashCode" -> System.identityHashCode(proxy)
                    m.name == "equals" -> proxy === args?.get(0)
                    else -> null
                }
            })
            val message = messageField.get(dialog) as? CharSequence ?: ""
            messageField.set(dialog, SpannableStringBuilder(message).append("(")
                .append(fix.url, StyleSpan(Typeface.BOLD), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE).append(")"))
        }
    }
}

private fun findOpenLinkDialog(bridge: DexKitBridge): Map<String, String> {
    val external = bridge.findMethod { matcher {
        addInvoke { declaredClass("org.telegram.messenger.SendMessagesHelper"); name("requestUrlAuth") }
        addInvoke { declaredClass("org.telegram.messenger.AndroidUtilities"); name("shouldShowUrlInAlert") }
    } }.single()
    check(external.paramTypeNames.take(3) == listOf("int", "java.lang.String", "android.text.style.CharacterStyle"))
    check(external.paramTypeNames[4] == "boolean")
    val alert = bridge.findMethod { matcher {
        paramCount(10)
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("OpenUrlTitle") }
    } }.single()
    check(alert.paramTypeNames.take(7) == listOf("android.content.Context", "java.lang.String", "boolean", "boolean", "boolean", "boolean", "long"))
    val browserClass = alert.invokes.single { it.paramCount == 10 && it.paramTypeNames.take(2) == listOf("android.content.Context", "android.net.Uri") }.className
    val open = bridge.findMethod { matcher {
        declaredClass(browserClass)
        paramTypes("android.content.Context", "android.net.Uri", "boolean", "boolean", alert.paramTypeNames[7])
        returnType("void")
    } }.single()
    val positive = alert.invokes.single { it.paramCount == 2 && it.paramTypeNames[0] == "java.lang.CharSequence" }
    val setters = bridge.getClassData(positive.className)!!.methods.filter {
        it.paramCount == 2 && it.paramTypeNames[0] in listOf("java.lang.String", "java.lang.CharSequence") && it.paramTypeNames[1] == positive.paramTypeNames[1]
    }
    val used = (alert.invokes + alert.invokes.flatMap { it.invokes }).map { it.descriptor }.toSet()
    val neutral = setters.single { it.descriptor !in used }
    val neutralFields = neutral.usingFields.filter { it.usingType == FieldUsingType.Write }.map { it.field }.distinctBy { it.descriptor }
    val text = neutralFields.single { it.typeName == "java.lang.CharSequence" }
    val listener = neutralFields.single { it.typeName == positive.paramTypeNames[1] }
    val dialog = bridge.getClassData(text.className)!!
    val show = dialog.methods.single { it.name == "show" && it.paramCount == 0 }
    val messageSetter = dialog.findMethod { matcher {
        paramCount(1); returnType("void")
        addInvoke { descriptor("Landroid/text/TextUtils;->isEmpty(Ljava/lang/CharSequence;)Z") }
        addUsingField { type("java.lang.CharSequence"); usingType(UsingType.Write) }
    } }.single()
    val message = messageSetter.usingFields.map { it.field }.distinctBy { it.descriptor }.single { it.typeName == "java.lang.CharSequence" }
    return mapOf("external" to external.descriptor, "alert" to alert.descriptor, "open" to open.descriptor,
        "show" to show.descriptor, "neutralText" to text.descriptor, "neutralListener" to listener.descriptor, "message" to message.descriptor)
}
