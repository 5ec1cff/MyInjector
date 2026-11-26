package io.github.a13e300.myinjector.telegram

import android.graphics.Canvas
import io.github.a13e300.myinjector.arch.ObfsTable
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.hookBefore
import io.github.a13e300.myinjector.arch.hookConstantIf
import io.github.a13e300.myinjector.arch.toObfsInfo
import io.github.a13e300.myinjector.logD
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod

class ProhibitChannelSwitching : MyDynHook("prohibitChannelSwitching") {
    override fun isFeatureEnabled(): Boolean = TelegramHandler.settings.prohibitChannelSwitching

    private fun deobf(creator: ObfsTableCreator): ObfsTable {
        val drawable = creator.create("ChatPullingDownDrawable") { bridge ->
            bridge.getClassData("org.telegram.ui.ChatPullingDownDrawable")?.let { return@create it.toObfsInfo() }
            bridge.findMethod {
                matcher {
                    usingNumbers(16f, 26f, 22f, 32f, 20f, 1f, 0.5f, 24f, 2f, 16 / 220f)
                }
            }.single().toObfsInfo()
        }
        val updateTopic = creator.create("ChatPullingDownUpdateTopic") { bridge ->
            bridge.findMethod {
                matcher {
                    declaredClass(drawable.className)
                    paramTypes()
                    returnType("void")
                    addUsingField {
                        declaredClass("org.telegram.tgnet.TLRPC\$TL_forumTopic")
                        name("icon_emoji_id")
                    }
                }
            }.single().toObfsInfo()
        }
        creator.create("ChatPullingDownNextTopic") { bridge ->
            bridge.getClassData(drawable.className)!!.fields.single {
                it.typeName == "org.telegram.tgnet.TLRPC\$TL_forumTopic"
            }.toObfsInfo()
        }
        creator.create("ChatPullingDownEmptyStub") { bridge ->
            val writes = scanTelegramDex(bridge, bridge.getMethodData(updateTopic.descriptor)!!).writes
                .filter { it.field.className == drawable.className && it.field.typeName == "boolean" }
            // emptyStub is false with a next topic and true without one.
            writes.map { it.field }.distinctBy { it.descriptor }.single { field ->
                writes.any { it.field == field && it.value?.number == 0 } &&
                    writes.any { it.field == field && it.value?.number == 1 }
            }.toObfsInfo()
        }
        return creator.obfsTable
    }

    override fun onHook() {
        val table = deobf(TelegramHandler.creator)
        val ChatPullingDownDrawable = table["ChatPullingDownDrawable"]!!

        val nextTopic = DexField(table.getValue("ChatPullingDownNextTopic").descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        val emptyStub = DexField(table.getValue("ChatPullingDownEmptyStub").descriptor)
            .getFieldInstance(classLoader).apply { isAccessible = true }
        // R8 can inline the topic lookup and getter. Set the state read by the touch handler.
        DexMethod(table.getValue("ChatPullingDownUpdateTopic").descriptor)
            .getMethodInstance(classLoader).apply { isAccessible = true }.hookBefore(cond = ::isEnabled) { param ->
                nextTopic.set(param.thisObject, null)
                emptyStub.setBoolean(param.thisObject, true)
                param.result = null
            }

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
