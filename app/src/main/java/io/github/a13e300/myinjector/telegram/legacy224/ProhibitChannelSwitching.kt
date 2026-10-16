package io.github.a13e300.myinjector.telegram.legacy224

import io.github.a13e300.myinjector.arch.DynHook
import android.graphics.Canvas
import android.view.View
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookConstantIf
import io.github.a13e300.myinjector.logD
import java.lang.reflect.Modifier

class ProhibitChannelSwitching : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.prohibitChannelSwitching

    override fun onHook() {
        check(loadPackageParam.appInfo.packageName == "com.exteragram.messenger") {
            "Channel pull mapping is only verified for Exteragram"
        }
        // Verified against exteraGram-full-universal-20261004.apk, classes6.dex:
        // this class and its methods retain their names. Do not reuse the upstream
        // draw() float-constant match or its cached obfuscation table entry.
        val drawable = findClass("org.telegram.ui.ChatPullingDownDrawable")
        val methods = drawable.declaredMethods
        // These names are retained in the supplied APK. Validate the complete
        // signatures before installing ANY hooks; unsupported layouts fail closed
        // rather than guessing every zero-argument boolean or returning null to a
        // primitive. Unrelated animationIsRunning/hasGradientService stay intact.
        fun method(name: String, result: Class<*>, vararg args: Class<*>) =
            methods.single {
                it.name == name && it.returnType == result &&
                    it.parameterTypes.contentEquals(args)
            }
        val selector = methods.single {
            it.name == "getNextUnreadDialog" && Modifier.isStatic(it.modifiers) &&
                it.returnType.name == "org.telegram.tgnet.TLRPC\$Dialog" &&
                it.parameterTypes.contentEquals(arrayOf(
                    java.lang.Long.TYPE, Integer.TYPE, Integer.TYPE,
                    java.lang.Boolean.TYPE, IntArray::class.java
                ))
        }
        val chatId = method("getChatId", java.lang.Long.TYPE)
        val draw = method("draw", java.lang.Void.TYPE, Canvas::class.java,
            View::class.java, java.lang.Float.TYPE, java.lang.Float.TYPE)
        val panel = method("drawBottomPanel", java.lang.Void.TYPE, Canvas::class.java,
            Integer.TYPE, Integer.TYPE, Integer.TYPE)
        val needPanel = method("needDrawBottomPanel", java.lang.Boolean.TYPE)
        val isTopic = drawable.getDeclaredField("isTopic").also {
            check(it.type == java.lang.Boolean.TYPE && !Modifier.isStatic(it.modifiers))
            it.isAccessible = true
        }
        check(listOf(chatId, draw, panel, needPanel).none { Modifier.isStatic(it.modifiers) })

        // updateDialog() consumes this nullable selector. Never hook generic chat
        // opening APIs: list/search/link/manual channel navigation is unaffected.
        selector.hookConstantIf(null, cond = ::isEnabled)
        // ChatActivity.animateToNextChat() gates channel replacement on this id.
        // This also covers already populated nextChat when enabling the switch,
        // and the recommended-channel overload that bypasses the unread selector.
        // Boxed Long (0L), not null/Int, is required for the primitive long result.
        chatId.hookConstantIf(0L, cond = ::isEnabled)

        // The drawable is shared with forum topics. Keep topic pull navigation
        // and its drawing intact; suppress only the channel affordance.
        for ((target, replacement) in listOf(draw to null, panel to null, needPanel to false)) {
            target.hookBefore(cond = ::isEnabled) { param ->
                if (!isTopic.getBoolean(param.thisObject)) param.result = replacement
            }
        }
        logD("channel pull hooks: ${selector}, ${chatId}, ${draw}, ${panel}, ${needPanel}")
    }
}
