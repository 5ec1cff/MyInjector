package io.github.a13e300.myinjector.douyin

internal enum class EmojiImageFormat(val extension: String, val mimeType: String) {
    GIF("gif", "image/gif"),
    WEBP("webp", "image/webp"),
    PNG("png", "image/png"),
    JPEG("jpg", "image/jpeg"),
    HEIF("heic", "image/heic");

    companion object {
        fun detect(header: ByteArray): EmojiImageFormat? = when {
            header.startsWithBytes("GIF87a".toByteArray()) ||
                header.startsWithBytes("GIF89a".toByteArray()) -> GIF
            header.size >= 12 && header.startsWithBytes("RIFF".toByteArray()) &&
                header.copyOfRange(8, 12).contentEquals("WEBP".toByteArray()) -> WEBP
            header.startsWithBytes(byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)) -> PNG
            header.startsWithBytes(byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())) -> JPEG
            header.isHeif() -> HEIF
            else -> null
        }

        private fun ByteArray.startsWithBytes(prefix: ByteArray): Boolean =
            size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }

        private fun ByteArray.isHeif(): Boolean {
            if (size < 16 || String(this, 4, 4, Charsets.US_ASCII) != "ftyp") return false
            // ftyp 的主品牌在 8，兼容品牌从 16 开始，12 是版本号。
            return (listOf(8) + (16..(size - 4) step 4)).any { offset ->
                String(this, offset, 4, Charsets.US_ASCII) in
                    setOf("heic", "heix", "hevc", "hevx", "mif1", "msf1")
            }
        }
    }
}
