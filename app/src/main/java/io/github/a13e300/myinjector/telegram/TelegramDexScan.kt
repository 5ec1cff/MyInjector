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
