package io.github.a13e300.myinjector.bili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiliNewShareTest {
    @Test
    fun copiesVideoTitleAndLink() {
        val url = "https://www.bilibili.com/video/BV1xx411c7mD?p=2"
        assertEquals("视频标题\n$url", formatBiliShareText("视频标题", "作者: 测试\n分 P 描述", url))
        assertEquals(url, formatBiliShareText(null, "作者: 测试", url))
    }

    @Test
    fun copiesDynamicTitleAndBodyWithoutTruncation() {
        val url = "https://t.bilibili.com/123456789"
        val body = "第一段正文\n\n" + "长文本🙂".repeat(100)
        assertEquals("图文标题\n$body\n$url", formatBiliShareText("图文标题", body, url))
        assertEquals("相同文本\n$url", formatBiliShareText("相同文本", "相同文本", url))
        assertEquals("正文\n$url", formatBiliShareText(" ", "正文", url))
        assertEquals("正文\n$url", formatBiliShareText(url, "正文", url))
        assertEquals(url, formatBiliShareText(null, null, url))
    }

    @Test
    fun keepsTextWhenPreparingLocalCopy() {
        val text = "标题\n第一段\n\n第二段🙂\nhttps://www.bilibili.com/opus/123456789?share_source=copy_link"
        assertEquals("标题\n第一段\n\n第二段🙂\nhttps://www.bilibili.com/opus/123456789", localBiliCopyText(text))
        assertNull(localBiliCopyText("文本里有链接 https://t.bilibili.com/123456789"))
        assertNull(localBiliCopyText("https://t.bilibili.com/123456789\n链接后还有正文"))
        assertNull(localBiliCopyText("标题\nhttps://b23.tv/abcdef"))
    }

    @Test
    fun preservesVideoPageAndOtherUrlData() {
        val url = "https://www.bilibili.com/video/BV1xx411c7mD?p=2&t=30&share_source=copy_link&share_medium=android#reply42"
        assertEquals("https://www.bilibili.com/video/BV1xx411c7mD?p=2&t=30#reply42", localBiliShareUrl(url))
        assertEquals("https://www.bilibili.com/video/av123", localBiliShareUrl("https://www.bilibili.com/video/av123"))
    }

    @Test
    fun acceptsDynamicAndOpusLinks() {
        assertEquals("https://t.bilibili.com/123456789", localBiliShareUrl("https://t.bilibili.com/123456789?share_session_id=test"))
        assertEquals("https://www.bilibili.com/opus/123456789", localBiliShareUrl("https://www.bilibili.com/opus/123456789"))
        val url = "https://t.bilibili.com/123456789?key=a%26b"
        assertEquals(url, localBiliShareUrl(url))
    }

    @Test
    fun leavesOtherCopyContentAlone() {
        listOf(null, "作者: 测试", "https://b23.tv/abcdef", "https://live.bilibili.com/123",
            "https://www.bilibili.com/bangumi/play/ep123", "bilibili://video/123",
            "https://www.bilibili.com.evil.com/video/av123", "https://t.bilibili.com@evil.com/123",
            "https://evil.com@t.bilibili.com/123", "https://t.bilibili.com:443/123",
            "https://t.bilibili.com/0", "https://www.bilibili.com/video/", "https://t.bilibili.com/123 bad")
            .forEach { assertNull(it, localBiliShareUrl(it)) }
    }
}
