package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.getInsnWide
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.result.MethodData

internal data class TgDexValue(
    val field: FieldData? = null,
    val number: Int? = null,
    val string: String? = null,
    val resource: String? = null,
    val bundleKey: String? = null,
)
internal data class TgDexCall(val offset: Int, val method: MethodData, val receiver: TgDexValue?, val args: List<TgDexValue?>)
internal data class TgDexWrite(val offset: Int, val field: FieldData, val value: TgDexValue?)
internal data class TgDexCompare(val offset: Int, val target: Int, val fields: List<FieldData>)
internal data class TgDexReturn(val offset: Int, val value: TgDexValue?)
internal data class TgDexCode(val calls: List<TgDexCall>, val writes: List<TgDexWrite>, val compares: List<TgDexCompare>, val returns: List<TgDexReturn>)

// Follow local register origins to associate resource labels with their UI
// receivers. This is not a control-flow interpreter: queries below validate
// unique, short patterns and use row reset blocks to collect index fields.
internal fun scanTelegramDex(bridge: DexKitBridge, method: MethodData): TgDexCode {
    val regs = mutableMapOf<Int, TgDexValue>()
    val calls = mutableListOf<TgDexCall>()
    val writes = mutableListOf<TgDexWrite>()
    val compares = mutableListOf<TgDexCompare>()
    val returns = mutableListOf<TgDexReturn>()
    val code = method.insns
    var pending: TgDexValue? = null
    var pos = 0
    fun assign(reg: Int, value: TgDexValue?) {
        regs.remove(reg)
        value?.let { regs[reg] = it }
    }
    while (pos < code.size) {
        val word = code[pos].code
        val op = word and 0xff
        val a = (word ushr 8) and 0xf
        val b = word ushr 12
        val aa = word ushr 8
        if (op !in 0x0a..0x0c) pending = null
        when (op) {
            in 0x01..0x09 -> {
                val dst = if (op in listOf(1, 4, 7)) a else if (op in listOf(2, 5, 8)) aa else code[pos + 1].code
                val src = if (op in listOf(1, 4, 7)) b else if (op in listOf(2, 5, 8)) code[pos + 1].code else code[pos + 2].code
                assign(dst, regs[src])
            }
            in 0x0a..0x0c -> assign(aa, pending)
            in 0x0f..0x11 -> returns += TgDexReturn(pos, regs[aa])
            0x12 -> assign(a, TgDexValue(number = b.shl(28).shr(28)))
            0x13 -> assign(aa, TgDexValue(number = code[pos + 1].code.toShort().toInt()))
            0x14 -> assign(aa, TgDexValue(number = code[pos + 1].code or (code[pos + 2].code shl 16)))
            0x15 -> assign(aa, TgDexValue(number = code[pos + 1].code shl 16))
            0x1a, 0x1b -> {
                val id = code[pos + 1].code or (if (op == 0x1b) code[pos + 2].code shl 16 else 0)
                assign(aa, TgDexValue(string = bridge.getStringByDexAndId(method.dexId, id)))
            }
            in 0x52..0x58 -> assign(a, TgDexValue(field = bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!))
            in 0x59..0x5f -> {
                val field = bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!
                writes += TgDexWrite(pos, field, regs[a])
                // Constructors store a new view and then call setters through
                // the same local register instead of reading the field again.
                if (op == 0x5b && field.className == method.className) assign(a, (regs[a] ?: TgDexValue()).copy(field = field))
            }
            in 0x60..0x66 -> {
                val field = bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!
                assign(aa, TgDexValue(field = field, resource = if (field.className.startsWith("org.telegram.messenger.R\$")) field.name else null))
            }
            in 0x32..0x37 -> compares += TgDexCompare(pos, pos + code[pos + 1].code.toShort(), listOfNotNull(regs[a]?.field, regs[b]?.field))
            in 0x38..0x3d -> compares += TgDexCompare(pos, pos + code[pos + 1].code.toShort(), listOfNotNull(regs[aa]?.field))
            in 0x6e..0x72, in 0x74..0x78 -> {
                val invoked = bridge.getMethodDataByDexAndId(method.dexId, code[pos + 1].code)!!
                val args = if (op >= 0x74) List(aa) { code[pos + 2].code + it } else {
                    val packed = code[pos + 2].code
                    listOf(packed and 0xf, (packed ushr 4) and 0xf, (packed ushr 8) and 0xf, packed ushr 12, a).take(b)
                }
                // External methods may have no definition in this dex, hence
                // no modifier metadata. The invoke opcode is authoritative.
                val static = op == 0x71 || op == 0x77
                val receiver = if (static) null else regs[args.first()]
                var index = if (static) 0 else 1
                val values = invoked.paramTypeNames.map { type ->
                    regs[args[index]].also { index += if (type == "long" || type == "double") 2 else 1 }
                }
                calls += TgDexCall(pos, invoked, receiver, values)
                if (invoked.className == "org.telegram.messenger.LocaleController" && invoked.name == "getString") {
                    pending = TgDexValue(resource = values.firstNotNullOfOrNull { it?.resource })
                } else if (invoked.className in listOf("android.os.Bundle", "android.os.BaseBundle") && invoked.name in listOf("getLong", "getInt")) {
                    pending = TgDexValue(bundleKey = values.first()?.string)
                } else if (invoked.modifiers and 0x1000 != 0 && invoked.invokes.isEmpty()) {
                    pending = invoked.usingFields.singleOrNull { it.usingType == FieldUsingType.Read }?.field?.let { TgDexValue(field = it) }
                } else if (invoked.returnTypeName != "void") {
                    pending = TgDexValue(resource = values.firstNotNullOfOrNull { it?.resource })
                }
            }
            0x23, in 0x7b..0x8f, in 0xb0..0xd7 -> assign(a, null)
            0x0d, in 0x16..0x19, 0x1c, in 0x21..0x22, in 0x2d..0x31,
            in 0x44..0x4a, in 0x90..0xaf, in 0xd8..0xe2 -> assign(aa, null)
        }
        pos += getInsnWide(code, pos)
    }
    return TgDexCode(calls, writes, compares, returns)
}

// R8 reuses constant registers across mutually exclusive menu branches. A
// linear scan can see an unrelated zero overwrite a menu ID. Merge constants
// along the actual control-flow edges before reading an invocation argument.
internal fun telegramIntArgument(bridge: DexKitBridge, method: MethodData, offset: Int, argument: Int): Int {
    val code = method.insns
    val states = mutableMapOf(0 to emptyMap<Int, Int>())
    val queue = ArrayDeque<Int>().apply { add(0) }
    fun intAt(pos: Int) = code[pos].code or (code[pos + 1].code shl 16)
    while (queue.isNotEmpty()) {
        val pos = queue.removeFirst()
        val values = states.getValue(pos).toMutableMap()
        val word = code[pos].code
        val op = word and 0xff
        val a = (word ushr 8) and 0xf
        val b = word ushr 12
        val aa = word ushr 8
        fun assign(reg: Int, value: Int?) { values.remove(reg); value?.let { values[reg] = it } }
        when (op) {
            0x01, 0x07 -> assign(a, values[b])
            0x02, 0x08 -> assign(aa, values[code[pos + 1].code])
            0x03, 0x09 -> assign(code[pos + 1].code, values[code[pos + 2].code])
            0x04 -> { assign(a, null); assign(a + 1, null) }
            0x05 -> { assign(aa, null); assign(aa + 1, null) }
            0x06 -> { val dst = code[pos + 1].code; assign(dst, null); assign(dst + 1, null) }
            0x12 -> assign(a, b.shl(28).shr(28))
            0x13 -> assign(aa, code[pos + 1].code.toShort().toInt())
            0x14 -> assign(aa, intAt(pos + 1))
            0x15 -> assign(aa, code[pos + 1].code shl 16)
            0x0b, in 0x16..0x19 -> { assign(aa, null); assign(aa + 1, null) }
            0x53 -> { assign(a, null); assign(a + 1, null) }
            0x61 -> { assign(aa, null); assign(aa + 1, null) }
            0x45, in 0x9b..0xa5, in 0xab..0xaf -> { assign(aa, null); assign(aa + 1, null) }
            0x7d, 0x7e, 0x80, 0x81, 0x83, 0x86, 0x88, 0x89, 0x8b, in 0xbb..0xc5, in 0xcb..0xcf -> {
                assign(a, null); assign(a + 1, null)
            }
            0x20, 0x23, in 0x52..0x58, in 0x7b..0x8f, in 0xb0..0xd7 -> assign(a, null)
            0x0a, 0x0c, 0x0d, in 0x1a..0x1c, 0x21, 0x22, in 0x2d..0x31,
            in 0x44..0x4a, in 0x60..0x66, in 0x90..0xaf, in 0xd8..0xe2 -> assign(aa, null)
        }
        val next = pos + getInsnWide(code, pos)
        val successors = when (op) {
            in 0x0e..0x11, 0x27 -> emptyList()
            0x28 -> listOf(pos + aa.toByte())
            0x29 -> listOf(pos + code[pos + 1].code.toShort())
            0x2a -> listOf(pos + intAt(pos + 1))
            in 0x32..0x3d -> listOf(next, pos + code[pos + 1].code.toShort())
            0x2b, 0x2c -> {
                val payload = pos + intAt(pos + 1)
                val count = code[payload + 1].code
                val keys = if (op == 0x2b) List(count) { intAt(payload + 2) + it }
                    else List(count) { intAt(payload + 2 + it * 2) }
                val targets = if (op == 0x2b) payload + 4 else payload + 2 + count * 2
                val value = values[aa]
                if (value == null) listOf(next) + List(count) { pos + intAt(targets + it * 2) }
                else keys.indexOf(value).let { if (it == -1) listOf(next) else listOf(pos + intAt(targets + it * 2)) }
            }
            else -> listOf(next)
        }
        for (target in successors.filter { it in code.indices }) {
            val previous = states[target]
            val merged = if (previous == null) values.toMap() else previous.filter { (reg, value) -> values[reg] == value }
            if (previous != merged) { states[target] = merged; queue.add(target) }
        }
    }
    val word = code[offset].code
    val op = word and 0xff
    check(op in 0x6e..0x72 || op in 0x74..0x78)
    val invoked = bridge.getMethodDataByDexAndId(method.dexId, code[offset + 1].code)!!
    check(invoked.paramTypeNames[argument] == "int")
    val registers = if (op >= 0x74) List(word ushr 8) { code[offset + 2].code + it } else {
        val packed = code[offset + 2].code
        listOf(packed and 0xf, (packed ushr 4) and 0xf, (packed ushr 8) and 0xf, packed ushr 12, (word ushr 8) and 0xf).take(word ushr 12)
    }
    val parameter = (if (op == 0x71 || op == 0x77) 0 else 1) +
        invoked.paramTypeNames.take(argument).map { if (it == "long" || it == "double") 2 else 1 }.sum()
    return states[offset]?.get(registers[parameter]) ?: error("Unknown integer argument $argument at ${method.descriptor}:$offset")
}
