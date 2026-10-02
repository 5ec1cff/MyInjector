package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.getInsnWide
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData
import org.luckypray.dexkit.wrap.DexField
import org.luckypray.dexkit.wrap.DexMethod
import java.lang.reflect.Modifier

internal fun deobfSaveSecretImage(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { findSaveSecretImage(creator.bridge, creator.obfsTable.getValue("BaseFragment").className) }
    val fields = setOf("selected", "provider", "dialog", "mergeDialog", "gallery", "gap")
    return listOf("fill", "messageType", "options", "selected", "open", "instance", "parent", "theme",
        "provider", "topic", "dialog", "mergeDialog", "gallery", "gap", "switch").associateWith { key ->
        creator.create("SaveSecretImage.$key") {
            val descriptor = found.getValue(key)
            if (key in fields) {
                val f = DexField(descriptor); ObfsInfo(f.className, f.name, descriptor)
            } else {
                val m = DexMethod(descriptor); ObfsInfo(m.className, m.name, descriptor)
            }
        }
    }
}

private data class SecretCall(val position: Int, val method: MethodData, val fields: List<FieldData?>)
private data class SecretWrite(val position: Int, val field: FieldData, val source: FieldData?, val result: SecretCall?)
private data class SecretCode(val calls: List<SecretCall>, val writes: List<SecretWrite>)

// Track field loads and invocation results around view creation and migration ID writes.
private fun secretCode(bridge: DexKitBridge, method: MethodData): SecretCode {
    val fields = mutableMapOf<Int, FieldData>()
    val results = mutableMapOf<Int, SecretCall>()
    val calls = mutableListOf<SecretCall>()
    val writes = mutableListOf<SecretWrite>()
    val insns = method.insns
    var pending: SecretCall? = null
    var pos = 0
    fun clear(reg: Int) { fields.remove(reg); results.remove(reg) }
    while (pos < insns.size) {
        val word = insns[pos].code
        val op = word and 0xff
        val a = (word ushr 8) and 0xf
        val b = word ushr 12
        val aa = word ushr 8
        if (op !in 0x0a..0x0c) pending = null
        when (op) {
            in 0x01..0x09, 0x7d -> {
                val dst = if (op in listOf(1, 4, 7, 0x7d)) a else if (op in listOf(2, 5, 8)) aa else insns[pos + 1].code
                val src = if (op in listOf(1, 4, 7, 0x7d)) b else if (op in listOf(2, 5, 8)) insns[pos + 1].code else insns[pos + 2].code
                val field = fields[src]; val result = results[src]
                clear(dst)
                field?.let { fields[dst] = it }; result?.let { results[dst] = it }
            }
            in 0x0a..0x0c -> { clear(aa); pending?.let { results[aa] = it } }
            in 0x52..0x58 -> { clear(a); fields[a] = bridge.getFieldDataByDexAndId(method.dexId, insns[pos + 1].code)!! }
            in 0x60..0x66 -> { clear(aa); fields[aa] = bridge.getFieldDataByDexAndId(method.dexId, insns[pos + 1].code)!! }
            in 0x59..0x5f -> {
                val field = bridge.getFieldDataByDexAndId(method.dexId, insns[pos + 1].code)!!
                writes += SecretWrite(pos, field, fields[a], results[a])
            }
            in 0x6e..0x72, in 0x74..0x78 -> {
                val invoked = bridge.getMethodDataByDexAndId(method.dexId, insns[pos + 1].code)!!
                val regs = if (op >= 0x74) List(aa) { insns[pos + 2].code + it } else {
                    val packed = insns[pos + 2].code
                    listOf(packed and 0xf, (packed ushr 4) and 0xf, (packed ushr 8) and 0xf, packed ushr 12, a).take(b)
                }
                val call = SecretCall(pos, invoked, regs.map { fields[it] })
                calls += call; pending = call
            }
            0x12, 0x23, in 0x7b..0x8f, in 0xb0..0xd7 -> clear(a)
            0x0d, in 0x13..0x1c, in 0x20..0x22, in 0x2d..0x31,
            in 0x44..0x4a, in 0x90..0xaf, in 0xd8..0xe2 -> clear(aa)
        }
        pos += getInsnWide(insns, pos)
    }
    return SecretCode(calls, writes)
}

private fun findSaveSecretImage(bridge: DexKitBridge, baseFragment: String): Map<String, String> {
    val fill = bridge.findMethod { matcher {
        paramTypes("org.telegram.messenger.MessageObject", "java.util.ArrayList", "java.util.ArrayList", "java.util.ArrayList")
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("SaveToGallery") }
    } }.single()
    val chat = fill.declaredClass!!
    val type = fill.invokes.single { it.className == chat.name && it.paramTypeNames == listOf("org.telegram.messenger.MessageObject") && it.returnTypeName == "int" }
    val options = chat.findMethod { matcher { paramTypes("int"); returnType("void"); usingEqStrings("tel:", "canSelectTopics", "messagesCount") } }.single()
    val selected = options.usingFields.first().field
    check(selected.typeName == "org.telegram.messenger.MessageObject" && selected.className == chat.name)
    val open = bridge.findMethod { matcher { paramCount(6); addCaller { declaredClass(chat.name) } } }.single {
        it.paramTypeNames.take(5) == listOf("org.telegram.messenger.MessageObject", chat.name, "long", "long", "long")
    }
    val pv = open.declaredClass!!
    val instance = pv.methods.single { it.paramCount == 0 && it.returnTypeName == pv.name && Modifier.isStatic(it.modifiers) && it.invokes.any { m -> m.isConstructor && m.className == pv.name } }
    val parent = pv.findMethod { matcher {
        paramCount(3); returnType("void")
        addUsingField { declaredClass("org.telegram.messenger.R\$drawable"); name("msg_gallery") }
    } }.single { it.paramTypeNames.take(2) == listOf("android.app.Activity", baseFragment) }
    val theme = chat.methods.single { it.paramCount == 0 && it.returnTypeName == parent.paramTypeNames[2] }
    val caller = open.callers.single { it.className == chat.name }
    val viewerProvider = caller.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
        it.className == chat.name && (it.typeName == open.paramTypeNames.last() || it.type.superClass?.name == open.paramTypeNames.last())
    }
    val topic = caller.invokes.single { it.className == chat.name && it.paramCount == 0 && it.returnTypeName == "long" }
    val migration = chat.findMethod { matcher {
        addUsingField { descriptor("Lorg/telegram/tgnet/TLRPC\$ChatFull;->migrated_from_chat_id:J") }
    } }.flatMap { secretCode(bridge, it).writes }.filter {
        it.field.className == chat.name && it.source?.descriptor == "Lorg/telegram/tgnet/TLRPC\$ChatFull;->migrated_from_chat_id:J"
    }.map { it.field }.distinctBy { it.descriptor }.single()
    val dialog = caller.usingFields.map { it.field }.distinctBy { it.descriptor }.single {
        it.className == chat.name && it.typeName == "long" && it.descriptor != migration.descriptor
    }
    val parentCode = secretCode(bridge, parent)
    val galleryWrite = parentCode.writes.single { it.result?.fields?.getOrNull(1)?.descriptor == "Lorg/telegram/messenger/R\$drawable;->msg_gallery:I" }
    // The gallery separator is the next gap created on the same menu after msg_gallery.
    val gapWrite = parentCode.writes.first {
        it.position > galleryWrite.position && it.result?.method?.className == galleryWrite.result!!.method.className &&
            it.result.method.paramTypeNames == listOf("int") &&
            it.result.method.returnType?.superClass?.name in listOf("android.view.View", "android.widget.FrameLayout")
    }
    val switch = pv.findMethod { matcher {
        paramTypes("int", "boolean", "boolean", "boolean")
        addUsingField { descriptor("Lorg/telegram/tgnet/TLRPC\$Message;->noforwards:Z") }
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("AttachDocument") }
    } }.single()
    return mapOf("fill" to fill.descriptor, "messageType" to type.descriptor, "options" to options.descriptor,
        "selected" to selected.descriptor, "open" to open.descriptor, "instance" to instance.descriptor,
        "parent" to parent.descriptor, "theme" to theme.descriptor, "provider" to viewerProvider.descriptor,
        "topic" to topic.descriptor, "dialog" to dialog.descriptor, "mergeDialog" to migration.descriptor,
        "gallery" to galleryWrite.field.descriptor, "gap" to gapWrite.field.descriptor, "switch" to switch.descriptor)
}
