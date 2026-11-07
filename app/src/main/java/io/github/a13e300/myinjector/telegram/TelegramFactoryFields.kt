package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.getInsnWide
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.MethodData

// Full SendMessageParams factory stores every argument. Follow moves so R8's
// reordered fields and high parameter registers do not change the mapping.
internal fun telegramFactoryFields(bridge: DexKitBridge, method: MethodData): List<FieldData> {
    val origins = mutableMapOf<Int, Int>()
    val writes = mutableListOf<Pair<Int, FieldData>>()
    val code = method.insns
    var pos = 0
    while (pos < code.size) {
        val word = code[pos].code
        val op = word and 0xff
        val a = (word ushr 8) and 15
        val b = word ushr 12
        val aa = word ushr 8
        when (op) {
            in 1..9 -> {
                val dst = if (op in listOf(1, 4, 7)) a else if (op in listOf(2, 5, 8)) aa else code[pos + 1].code
                val src = if (op in listOf(1, 4, 7)) b else if (op in listOf(2, 5, 8)) code[pos + 1].code else code[pos + 2].code
                origins[dst] = origins[src] ?: src
            }
            in 0x59..0x5f -> {
                val field = bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!
                if (field.className == method.className) writes += (origins[a] ?: a) to field
            }
        }
        pos += getInsnWide(code, pos)
    }
    check(writes.size == method.paramCount)
    val byRegister = writes.toMap()
    var register = byRegister.keys.min()
    return method.paramTypeNames.map { type ->
        val field = byRegister.getValue(register)
        check(field.typeName == type)
        register += if (type == "long" || type == "double") 2 else 1
        field
    }
}
