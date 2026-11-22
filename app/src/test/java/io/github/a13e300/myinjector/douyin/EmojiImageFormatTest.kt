package io.github.a13e300.myinjector.douyin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EmojiImageFormatTest {
    @Test fun keepsGifAndWebpFileTypes() {
        assertEquals(EmojiImageFormat.GIF, EmojiImageFormat.detect("GIF87a".toByteArray()))
        assertEquals(EmojiImageFormat.GIF, EmojiImageFormat.detect("GIF89a".toByteArray()))
        assertEquals("gif", EmojiImageFormat.detect("GIF89a".toByteArray())!!.extension)
        assertEquals("image/gif", EmojiImageFormat.detect("GIF89a".toByteArray())!!.mimeType)
        val webp = "RIFF".toByteArray() + byteArrayOf(16, 0, 0, 0) + "WEBP".toByteArray()
        assertEquals(EmojiImageFormat.WEBP, EmojiImageFormat.detect(webp))
        assertEquals("image/webp", EmojiImageFormat.detect(webp)!!.mimeType)
    }

    @Test fun recognizesStaticImages() {
        assertEquals(EmojiImageFormat.PNG, EmojiImageFormat.detect(
            byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)))
        assertEquals(EmojiImageFormat.JPEG, EmojiImageFormat.detect(
            byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte(), 0xe0.toByte())))
    }

    @Test fun rejectsErrorPagesAndIncompleteSignatures() {
        assertNull(EmojiImageFormat.detect("<html>error</html>".toByteArray()))
        assertNull(EmojiImageFormat.detect(byteArrayOf()))
        assertNull(EmojiImageFormat.detect("GIF89".toByteArray()))
        assertNull(EmojiImageFormat.detect("RIFF1234WAVE".toByteArray()))
        assertNull(EmojiImageFormat.detect("RIFF1234WEB".toByteArray()))
        assertNull(EmojiImageFormat.detect("GIFxxx".toByteArray()))
    }
}
