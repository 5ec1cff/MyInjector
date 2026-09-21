package io.github.a13e300.myinjector.telegram

import android.graphics.Canvas
import io.github.a13e300.myinjector.arch.DynHook
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookConstantIf
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD

class ProhibitChannelSwitching : DynHook() {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.prohibitChannelSwitching

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        creator.create("ChatPullingDownDrawable") { bridge ->
            bridge.findMethod {
                matcher {
                    usingNumbers(16f, 26f, 22f, 32f, 20f, 1f, 0.5f, 24f, 2f, 16 / 220f)
                }
            }.single().toObfsInfo()
        }
        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val ChatPullingDownDrawable = table["ChatPullingDownDrawable"]!!

        findClass(ChatPullingDownDrawable.className).declaredMethods.forEach {
            if (
            // draw
                (it.parameterCount == 4 && it.parameterTypes[0] == Canvas::class.java && it.parameterTypes[2] == java.lang.Float.TYPE && it.parameterTypes[3] == java.lang.Float.TYPE)
                ||
                // drawBottomPanel
                // r8 removed unused param 4
                (it.parameterCount >= 3 && it.parameterTypes[0] == Canvas::class.java && it.parameterTypes[1] == Integer.TYPE && it.parameterTypes[2] == Integer.TYPE)
                ||
                // getNextUnreadDialog
                (it.parameterCount == 5 && it.parameterTypes[0] == java.lang.Long.TYPE && it.parameterTypes[1] == Integer.TYPE && it.parameterTypes[2] == Integer.TYPE && it.parameterTypes[3] == java.lang.Boolean.TYPE && it.parameterTypes[4] == IntArray::class.java)
            ) {
                logD("hook $it")
                it.hookConstantIf(null, cond = ::isEnabled)
            } else if (it.parameterCount == 0 && it.returnType == java.lang.Boolean.TYPE) { // needDrawBottomPanel
                logD("hook $it")
                it.hookConstantIf(false, cond = ::isEnabled)
            }
        }

    }
}