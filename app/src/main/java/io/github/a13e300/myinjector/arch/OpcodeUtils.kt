package io.github.a13e300.myinjector.arch


private val opCodeWide = intArrayOf(
    1, 1, 2, 3, 1, 2, 3, 1,
    2, 3, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 2, 3, 2, 2, 3,
    5, 2, 2, 3, 2, 1, 1, 2,
    2, 1, 2, 2, 3, 3, 3, 1,
    1, 2, 3, 3, 3, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 1, 1,
    1, 1, 1, 1, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 3, 3,
    3, 3, 3, 1, 3, 3, 3, 3,
    3, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    1, 1, 1, 1, 1, 1, 1, 1,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 2, 2, 2, 2, 2, 2, 2,
    2, 3, 3, 2, 2, 2, 2, 2,
    2, 2, 2, 1, 1, 1, 1, 1,
    1, 1, 4, 4, 3, 3, 2, 2
)

fun getInsnWide(arr: CharArray, pos: Int): Int {
    require(pos >= 0 && pos < arr.size)
    val c = arr[pos].code
    if (c == 0x100) {
        // packed switch payload
        require(pos + 1 < arr.size)
        return 4 + arr[pos + 1].code.toUInt().toInt() * 2
    } else if (c == 0x200) {
        require(pos + 1 < arr.size)
        return 2 + arr[pos + 1].code.toUInt().toInt() * 4
    } else if (c == 0x300) {
        require(pos + 3 < arr.size)
        val elemWidth = arr[pos + 1].code.toUInt().toInt()
        val len = arr[pos + 2].code.toUInt().or(arr[pos + 3].code.toUInt().shl(16)).toInt()
        return 4 + (elemWidth * len + 1) / 2
    }
    return opCodeWide[c.and(0xff)]
}

enum class InvokeType {
    Virtual,
    Static,
}

data class Invoke(
    val type: InvokeType,
    val regs: IntArray,
    val ref: Int,
)

fun decodeInvoke(arr: CharArray, pos: Int): Invoke? {
    if (pos + 2 >= arr.size) return null
    val agop = arr[pos].code
    val bbbb = arr[pos + 1].code
    val fedc = arr[pos + 2].code
    val type = when (agop.and(0xff)) {
        0x6e -> InvokeType.Virtual
        0x71 -> InvokeType.Static
        else -> return null
    }
    // require(agop.and(0xff) == 0x6e)
    val ag = agop.ushr(8)
    val g = ag.and(0xf)
    val a = ag.ushr(4)
    val f = fedc.ushr(12)
    val e = fedc.ushr(8).and(0xf)
    val d = fedc.ushr(4).and(0xf)
    val c = fedc.and(0xf)
    val regs = when (a) {
        0 -> intArrayOf()
        1 -> intArrayOf(c)
        2 -> intArrayOf(c, d)
        3 -> intArrayOf(c, d, e)
        4 -> intArrayOf(c, d, e, f)
        5 -> intArrayOf(c, d, e, f, g)
        else -> error("invalid a: $a")
    }
    return Invoke(type, regs, bbbb)
}

enum class IInstanceOpType {
    IGet,
    IGetWide,
    IGetObject,
    IGetBoolean,
    IGetByte,
    IGetChar,
    IGetShort,
    IPut,
    IPutWide,
    IPutObject,
    IPutBoolean,
    IPutByte,
    IPutChar,
    IPutShort,
}

data class IInstanceOp(
    val type: IInstanceOpType,
    val srcReg: Int,
    val objReg: Int,
    val ref: Int,
)

fun decodeIInstanceOp(arr: CharArray, pos: Int): IInstanceOp? {
    if (pos + 1 >= arr.size) return null
    val baop = arr[pos].code
    val cccc = arr[pos + 1].code
    val type = when (baop.and(0xff)) {
        0x52 -> IInstanceOpType.IGet
        0x53 -> IInstanceOpType.IGetWide
        0x54 -> IInstanceOpType.IGetObject
        0x55 -> IInstanceOpType.IGetBoolean
        0x56 -> IInstanceOpType.IGetByte
        0x57 -> IInstanceOpType.IGetChar
        0x58 -> IInstanceOpType.IGetShort
        0x59 -> IInstanceOpType.IPut
        0x5a -> IInstanceOpType.IPutWide
        0x5b -> IInstanceOpType.IPutObject
        0x5c -> IInstanceOpType.IPutBoolean
        0x5d -> IInstanceOpType.IPutByte
        0x5e -> IInstanceOpType.IPutChar
        0x5f -> IInstanceOpType.IPutShort
        else -> return null
    }
    val a = baop.ushr(8).and(0xf)
    val b = baop.ushr(12)
    return IInstanceOp(type = type, srcReg = a, objReg = b, ref = cccc)
}

data class ConstHigh16(
    val dstReg: Int,
    val value: Int,
)

fun decodeConstHigh16(arr: CharArray, pos: Int): ConstHigh16? {
    if (pos + 1 >= arr.size) return null
    val aaop = arr[pos].code
    val bbbb = arr[pos + 1].code
    if (aaop.and(0xff) != 0x15) return null
    val aa = aaop.ushr(8)
    return ConstHigh16(dstReg = aa, value = bbbb.shl(16))
}

data class ConstString(
    val reg: Int,
    val ref: Int,
)

fun decodeConstString(arr: CharArray, pos: Int): ConstString? {
    if (pos + 1 >= arr.size) return null
    val aaop = arr[pos].code
    val bbbb = arr[pos + 1].code
    if (aaop.and(0xff) != 0x1a) return null
    val aa = aaop.shr(8)
    return ConstString(reg = aa, ref = bbbb)
}


enum class SInstanceOpType {
    SGet,
    SGetWide,
    SGetObject,
    SGetBoolean,
    SGetByte,
    SGetChar,
    SGetShort,
    SPut,
    SPutWide,
    SPutObject,
    SPutBoolean,
    SPutByte,
    SPutChar,
    SPutShort,
}

data class SInstanceOp(
    val type: SInstanceOpType,
    val reg: Int,
    val ref: Int,
)

fun decodeSInstanceOp(arr: CharArray, pos: Int): SInstanceOp? {
    require(pos + 1 < arr.size)
    val aaop = arr[pos].code
    val bbbb = arr[pos + 1].code
    val type = when (aaop.and(0xff)) {
        0x60 -> SInstanceOpType.SGet
        0x61 -> SInstanceOpType.SGetWide
        0x62 -> SInstanceOpType.SGetObject
        0x63 -> SInstanceOpType.SGetBoolean
        0x64 -> SInstanceOpType.SGetByte
        0x65 -> SInstanceOpType.SGetChar
        0x66 -> SInstanceOpType.SGetShort
        0x67 -> SInstanceOpType.SPut
        0x68 -> SInstanceOpType.SPutWide
        0x69 -> SInstanceOpType.SPutObject
        0x6a -> SInstanceOpType.SPutBoolean
        0x6b -> SInstanceOpType.SPutByte
        0x6c -> SInstanceOpType.SPutChar
        0x6d -> SInstanceOpType.SPutShort
        else -> return null
    }
    val a = aaop.ushr(8)
    return SInstanceOp(type = type, reg = a, ref = bbbb)
}
