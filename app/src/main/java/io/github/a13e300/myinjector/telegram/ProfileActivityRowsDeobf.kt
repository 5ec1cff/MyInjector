package io.github.a13e300.myinjector.telegram

import io.github.a13e300.myinjector.arch.ObfsInfo
import io.github.a13e300.myinjector.arch.ObfsTableCreator
import io.github.a13e300.myinjector.arch.getInsnWide
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.result.FieldData
import org.luckypray.dexkit.result.FieldUsingType
import org.luckypray.dexkit.result.MethodData

private data class ProfileValue(
    val field: FieldData? = null,
    val number: Int? = null,
    val string: String? = null,
    val bundleKey: String? = null,
)

private data class ProfileRead(val offset: Int, val field: FieldData)
private data class ProfileWrite(val field: FieldData, val value: ProfileValue?)
private data class ProfileCompare(val offset: Int, val fields: List<FieldData>)
private data class ProfileCode(
    val reads: List<ProfileRead>,
    val writes: List<ProfileWrite>,
    val compares: List<ProfileCompare>,
)

// Follow register origins only around the local patterns inspected below. This also
// handles Java's synthetic field getters when R8 has not inlined them.
private fun profileCode(bridge: DexKitBridge, method: MethodData, prefixOnly: Boolean = false): ProfileCode {
    val regs = mutableMapOf<Int, ProfileValue>()
    val reads = mutableListOf<ProfileRead>()
    val writes = mutableListOf<ProfileWrite>()
    val compares = mutableListOf<ProfileCompare>()
    val code = method.insns
    var pending: ProfileValue? = null
    var pos = 0
    fun assign(reg: Int, value: ProfileValue?) {
        regs.remove(reg)
        value?.let { regs[reg] = it }
    }
    while (pos < code.size) {
        val word = code[pos].code
        val op = word and 0xff
        val a = (word ushr 8) and 0xf
        val b = word ushr 12
        val aa = word ushr 8
        // The row reset block is straight-line code, before the first branch.
        if (prefixOnly && (op in 0x0e..0x11 || op in 0x27..0x2c || op in 0x32..0x3d)) break
        if (op !in 0x0a..0x0c) pending = null
        when (op) {
            in 0x01..0x09 -> {
                val dst = if (op in listOf(1, 4, 7)) a else if (op in listOf(2, 5, 8)) aa else code[pos + 1].code
                val src = if (op in listOf(1, 4, 7)) b else if (op in listOf(2, 5, 8)) code[pos + 1].code else code[pos + 2].code
                assign(dst, regs[src])
            }
            in 0x0a..0x0c -> assign(aa, pending)
            0x12 -> assign(a, ProfileValue(number = b.shl(28).shr(28)))
            0x13 -> assign(aa, ProfileValue(number = code[pos + 1].code.toShort().toInt()))
            0x14 -> assign(aa, ProfileValue(number = code[pos + 1].code or (code[pos + 2].code shl 16)))
            0x1a, 0x1b -> {
                val id = code[pos + 1].code or (if (op == 0x1b) code[pos + 2].code shl 16 else 0)
                assign(aa, ProfileValue(string = bridge.getStringByDexAndId(method.dexId, id)))
            }
            in 0x52..0x58 -> {
                val field = bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!
                reads += ProfileRead(pos, field)
                assign(a, ProfileValue(field = field))
            }
            in 0x59..0x5f -> writes += ProfileWrite(
                bridge.getFieldDataByDexAndId(method.dexId, code[pos + 1].code)!!, regs[a]
            )
            in 0x32..0x37 -> compares += ProfileCompare(pos, listOfNotNull(regs[a]?.field, regs[b]?.field))
            in 0x6e..0x72, in 0x74..0x78 -> {
                val invoked = bridge.getMethodDataByDexAndId(method.dexId, code[pos + 1].code)!!
                val args = if (op >= 0x74) List(aa) { code[pos + 2].code + it } else {
                    val packed = code[pos + 2].code
                    listOf(packed and 0xf, (packed ushr 4) and 0xf, (packed ushr 8) and 0xf, packed ushr 12, a).take(b)
                }
                if (invoked.className in listOf("android.os.Bundle", "android.os.BaseBundle") && invoked.name in listOf("getLong", "getInt")) {
                    pending = ProfileValue(bundleKey = regs[args[1]]?.string)
                } else if (invoked.modifiers and 0x1000 != 0 && invoked.returnTypeName == "int") {
                    val field = invoked.usingFields.singleOrNull { it.usingType == FieldUsingType.Read }?.field
                    if (field != null && invoked.invokes.isEmpty()) pending = ProfileValue(field = field)
                }
            }
            0x23, in 0x7b..0x8f, in 0xb0..0xd7 -> assign(a, null)
            0x0d, in 0x15..0x19, 0x1c, in 0x20..0x22, in 0x2d..0x31,
            in 0x44..0x4a, in 0x60..0x66, in 0x90..0xaf, in 0xd8..0xe2 -> assign(aa, null)
        }
        pos += getInsnWide(code, pos)
    }
    return ProfileCode(reads, writes, compares)
}

internal fun findProfileActivityRows(bridge: DexKitBridge, baseFragment: String): Map<String, String> {
    val found = linkedMapOf<String, String>()
    fun save(key: String, method: MethodData) { found[key] = method.descriptor }
    fun save(key: String, field: FieldData) { found[key] = field.descriptor }
    val create = bridge.findMethod { matcher {
        declaredClass { superClass(baseFragment) }
        usingEqStrings("user_id", "chat_id", "topic_id", "my_profile")
        paramTypes(); returnType("boolean")
    } }.single()
    val profile = create.className
    val initial = profileCode(bridge, create).writes
    for (key in listOf("user_id", "chat_id", "topic_id")) {
        save(key, initial.single { it.field.className == profile && it.value?.bundleKey == key }.field)
    }
    val update = bridge.findMethod { matcher {
        declaredClass(profile); paramTypes(); returnType("void")
        usingEqStrings("PREMIUM_GRACE", "VALIDATE_PHONE_NUMBER", "VALIDATE_PASSWORD")
    } }.single()
    save("updateRows", update)
    val resets = profileCode(bridge, update, prefixOnly = true).writes.filter {
        it.field.className == profile && it.field.typeName == "int"
    }
    val rowFields = resets.filter { it.value?.number == -1 }.map { it.field }.distinctBy { it.descriptor }
    check(rowFields.size > 50) { "ProfileActivity row reset block not found" }
    // Includes interval boundaries (membersStartRow/membersEndRow), not just cells
    // registered by DiffCallback.fillPositions. Never infer fields from runtime values.
    found["rowFields"] = rowFields.joinToString("\n") { it.descriptor }
    save("rowCount", resets.single { it.value?.number == 0 }.field)
    val bind = bridge.findMethod { matcher {
        addUsingField { declaredClass("org.telegram.messenger.R\$string"); name("PhoneMobile") }
        paramCount(2); returnType("void")
        addUsingField { declaredClass(profile); type("int") }
    } }.single { it.paramTypeNames.last() == "int" }
    save("bind", bind)
    val adapter = bind.className
    val holder = bind.paramTypeNames.first()
    val methods = bridge.findMethod { matcher { declaredClass(adapter) } }
    save("viewType", methods.single { it.paramTypeNames == listOf("int") && it.returnTypeName == "int" })
    save("createHolder", methods.single {
        it.paramTypeNames == listOf("android.view.ViewGroup", "int") && it.returnTypeName == holder
    })
    val enabled = methods.single { it.paramTypeNames == listOf(holder) && it.returnTypeName == "boolean" }
    save("isEnabled", enabled)
    save("holderPosition", enabled.invokes.distinctBy { it.descriptor }.single {
        it.className == holder && it.paramTypeNames.isEmpty() && it.returnTypeName == "int" &&
            it.invokes.any { call -> call.paramTypeNames == listOf(holder) && call.returnTypeName == "int" }
    })
    save("holderView", bridge.findField { matcher { declaredClass(holder); type("android.view.View") } }.single())
    save("adapterProfile", bridge.findField { matcher { declaredClass(adapter); type(profile) } }.single())
    save("setTextAndValue", bind.invokes.distinctBy { it.descriptor }.single {
        it.paramTypeNames == listOf("java.lang.CharSequence", "java.lang.CharSequence", "boolean") &&
            it.returnTypeName == "void"
    })
    val bindCode = profileCode(bridge, bind)
    val rowDescriptors = rowFields.map { it.descriptor }.toSet()
    // infoSectionRow selects the separator containing bot verification. R8 removes
    // the unused infoStartRow/infoEndRow, so insert before this separator instead.
    val section = bindCode.reads.filter { it.field.fieldName == "bot_verification" }.map { read ->
        bindCode.compares.last { cmp -> cmp.offset < read.offset && cmp.fields.any { it.descriptor in rowDescriptors } }
            .fields.single { it.descriptor in rowDescriptors }
    }.distinctBy { it.descriptor }.single()
    save("infoSectionRow", section)
    save("click", bridge.findMethod { matcher {
        declaredClass(profile); returnType("void")
        usingEqStrings("addContact", "phone", "first_name_card", "last_name_card")
    } }.single { it.paramTypeNames.takeLast(4) == listOf("android.view.View", "int", "float", "float") })
    val fill = bridge.findMethod { matcher {
        paramTypes("android.util.SparseIntArray"); returnType("void")
        addInvoke { descriptor("Landroid/util/SparseIntArray;->clear()V") }
        addUsingField { declaredClass(profile); type("int") }
    } }.single { it.usingFields.count { f -> f.field.className == profile && f.field.typeName == "int" } > 50 }
    save("fillPositions", fill)
    save("diffProfile", bridge.findField { matcher { declaredClass(fill.className); type(profile) } }.single())
    return found
}

internal fun profileActivityRowsMembers(creator: ObfsTableCreator): Map<String, ObfsInfo> {
    val found by lazy { findProfileActivityRows(creator.bridge, creator.obfsTable.getValue("BaseFragment").className) }
    val keys = listOf("user_id", "chat_id", "topic_id", "updateRows", "rowFields", "rowCount", "bind",
        "viewType", "createHolder", "isEnabled", "holderPosition", "holderView", "adapterProfile",
        "setTextAndValue", "infoSectionRow", "click", "fillPositions", "diffProfile")
    return keys.associateWith { key ->
        creator.create("ProfileRows.$key") {
            val descriptor = found.getValue(key)
            // rowFields stores a list of full field descriptors in one cache entry.
            ObfsInfo(descriptor.substringBefore(';').removePrefix("L").replace('/', '.'), "", descriptor)
        }
    }
}
