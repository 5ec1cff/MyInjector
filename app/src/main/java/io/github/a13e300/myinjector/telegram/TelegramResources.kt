package io.github.a13e300.myinjector.telegram

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import org.luckypray.dexkit.DexKitBridge
import org.luckypray.dexkit.query.matchers.MethodMatcher

internal object TelegramResources {
    var apkPaths: List<String> = emptyList()
        set(value) {
            if (field != value) { field = value; loaded = null }
        }
    private var loaded: TelegramResourceTable? = null
    val table: TelegramResourceTable get() = loaded ?: TelegramResourceTable(apkPaths).also { loaded = it }
}

// Only reads names and IDs. This also works before Application creates its Context.
internal class TelegramResourceTable(paths: List<String>) {
    private val ids = mutableMapOf<String, Int>()
    private val names = mutableMapOf<Int, String>()
    private val stringHashes = mutableMapOf<Int, Int>()
    private val stringIds = mutableMapOf<Int, Int>()

    init {
        require(paths.isNotEmpty()) { "Telegram APK paths are missing" }
        for (path in paths) ZipFile(path).use { zip ->
            zip.getEntry("resources.arsc")?.let { entry ->
                readTable(zip.getInputStream(entry).use { it.readBytes() })
            }
            // Newer Telegram stores localized strings outside resources.arsc.
            zip.getEntry("assets/string_resource_ids.bin")?.let { entry ->
                val data = ByteBuffer.wrap(zip.getInputStream(entry).use { it.readBytes() }).order(ByteOrder.LITTLE_ENDIAN)
                repeat(data.int) {
                    val id = data.int
                    val hash = data.int
                    stringHashes[id] = hash
                    stringIds[hash] = id
                }
            }
        }
    }

    fun id(type: String, name: String): Int = ids["$type/$name"]
        ?: (if (type == "string") stringIds[name.hashCode()] else null)
        ?: error("Telegram resource not found: $type/$name")

    fun name(id: Int): String? = names[id]
    fun contains(id: Int): Boolean = id in names || id in stringHashes
    fun matches(id: Int, name: String): Boolean = names[id] == name || stringHashes[id] == name.hashCode()

    private fun readTable(bytes: ByteArray) {
        val data = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        fun u16(at: Int) = data.getShort(at).toInt() and 0xffff
        fun i32(at: Int) = data.getInt(at)
        fun children(start: Int, end: Int, visit: (Int, Int) -> Unit) {
            var at = start
            while (at < end) {
                val size = i32(at + 4)
                require(size >= u16(at + 2) && size >= 8 && size <= end - at)
                visit(at, u16(at))
                at += size
            }
        }
        fun pool(at: Int): List<String> {
            val count = i32(at + 8)
            val utf8 = i32(at + 16) and 0x100 != 0
            val start = at + i32(at + 20)
            return List(count) { index ->
                var pos = start + i32(at + u16(at + 2) + index * 4)
                fun length8(): Int {
                    val first = bytes[pos++].toInt() and 0xff
                    return if (first and 0x80 == 0) first else ((first and 0x7f) shl 8) or (bytes[pos++].toInt() and 0xff)
                }
                if (utf8) {
                    length8() // UTF-16 length precedes the UTF-8 byte length.
                    val length = length8()
                    String(bytes, pos, length, Charsets.UTF_8)
                } else {
                    val first = u16(pos)
                    pos += 2
                    val length = if (first and 0x8000 == 0) first else (((first and 0x7fff) shl 16) or u16(pos)).also { pos += 2 }
                    String(bytes, pos, length * 2, Charsets.UTF_16LE)
                }
            }
        }
        require(u16(0) == 2)
        children(u16(2), i32(4)) { pkg, kind ->
            if (kind == 0x200) {
                val packageId = i32(pkg + 8)
                val typeNames = pool(pkg + i32(pkg + 268))
                val keyNames = pool(pkg + i32(pkg + 276))
                val typeOffset = if (u16(pkg + 2) >= 288) i32(pkg + 284) else 0
                children(pkg + u16(pkg + 2), pkg + i32(pkg + 4)) { chunk, childKind ->
                    if (childKind == 0x201) {
                        val type = bytes[chunk + 8].toInt() and 0xff
                        val flags = bytes[chunk + 9].toInt() and 0xff
                        val entries = chunk + i32(chunk + 16)
                        val offsets = chunk + u16(chunk + 2)
                        repeat(i32(chunk + 12)) { index ->
                            val entryIndex: Int
                            val offset: Int
                            if (flags and 1 != 0) {
                                entryIndex = u16(offsets + index * 4)
                                offset = u16(offsets + index * 4 + 2) * 4
                            } else {
                                entryIndex = index
                                offset = if (flags and 2 != 0) u16(offsets + index * 2).let { if (it == 0xffff) -1 else it * 4 }
                                    else i32(offsets + index * 4)
                            }
                            if (offset != -1) {
                                val entry = entries + offset
                                val keyIndex = if (u16(entry + 2) and 8 != 0) u16(entry) else i32(entry + 4)
                                val name = keyNames[keyIndex]
                                val id = (packageId shl 24) or ((type + typeOffset) shl 16) or entryIndex
                                ids["${typeNames[type - 1]}/$name"] = id
                                names[id] = name
                            }
                        }
                    }
                }
            }
        }
    }
}

internal fun MethodMatcher.usingTelegramResource(bridge: DexKitBridge, type: String, name: String): MethodMatcher = apply {
    if (bridge.getClassData("org.telegram.messenger.R\$$type") != null) {
        addUsingField { descriptor("Lorg/telegram/messenger/R\$$type;->$name:I") }
    } else {
        addUsingNumber(TelegramResources.table.id(type, name))
    }
}
