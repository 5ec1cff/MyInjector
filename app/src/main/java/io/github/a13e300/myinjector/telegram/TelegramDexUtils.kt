package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.getInsnWide
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

// Follow straight-line register origins around resource assignments to distinguish
// multiple view fields of the same type, including views kept in constructor locals.
internal fun viewFieldWithResource(bridge: DexKitBridge, method: MethodData, resource: String): FieldData {
    val origins = mutableMapOf<Int, FieldData>()
    val matches = mutableListOf<FieldData>()
    val code = method.insns
    var pos = 0
    while (pos < code.size) {
        val word = code[pos].code
        val op = word and 0xff
        val a = (word ushr 8) and 0xf
        val b = word ushr 12
        val aa = word ushr 8
        when (op) {
            in 0x01..0x09 -> {
                val dst = if (op in listOf(1, 4, 7)) a else if (op in listOf(2, 5, 8)) aa else code[pos + 1].code
                val src = if (op in listOf(1, 4, 7)) b else if (op in listOf(2, 5, 8)) code[pos + 1].code else code[pos + 2].code
                val origin = origins[src]
                origins.remove(dst)
                origin?.let { origins[dst] = it }
            }
            in 0x52..0x58 -> origins[a] = bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!
            in 0x60..0x66 -> origins[aa] = bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!
            // A newly allocated view may be kept in a local after being stored in this.field.
            0x5b -> {
                val field = bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!
                if (field.className == method.className) origins[a] = field
            }
            in 0x6e..0x72, in 0x74..0x78 -> {
                val invoked = bridge.getMethodDataByDexAndId(method.dexId, code[pos + 1].code)!!
                val regs = if (op >= 0x74) List(aa) { code[pos + 2].code + it } else {
                    val packed = code[pos + 2].code
                    listOf(packed and 0xf, (packed ushr 4) and 0xf, (packed ushr 8) and 0xf, packed ushr 12, a).take(b)
                }
                if (invoked.name == "setImageResource" && regs.size == 2 && origins[regs[1]]?.fieldName == resource) {
                    origins[regs[0]]?.let { matches += it }
                }
            }
            0x12, 0x23, in 0x7b..0x8f, in 0xb0..0xd7 -> origins.remove(a)
            in 0x0a..0x0d, in 0x13..0x1c, in 0x20..0x22, in 0x2d..0x31,
            in 0x44..0x4a, in 0x90..0xaf, in 0xd8..0xe2 -> origins.remove(aa)
        }
        pos += getInsnWide(code, pos)
    }
    return matches.distinctBy { it.descriptor }.single()
}
