package io.github.a13e300.myinjector.xhs

import java.net.URI
import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class CommentShareTextTest {
    @Test fun buildsLongUrlWithOnlyRequiredParams() {
        val token = "a+b/c= &中文"
        val url = buildXhsCommentUrl("note123", "reply456", "video", token)
        val uri = URI(url)
        assertEquals("https", uri.scheme)
        assertEquals("www.xiaohongshu.com", uri.host)
        assertEquals("/discovery/item/note123", uri.path)
        assertEquals(mapOf("xsec_token" to token, "anchorCommentId" to "reply456", "type" to "video"),
            uri.rawQuery.split("&").associate {
                it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8")
            })
        assertFalse(url.contains(" "))
    }

    @Test fun clickCopiesOnlyLinkWithHtml() {
        val url = buildXhsCommentUrl("note", "comment", "normal", "token")
        val clip = buildXhsCommentText(url, "小红薯", null)
        assertEquals(url, clip.text)
        val htmlUrl = "https://www.xiaohongshu.com/discovery/item/note?" +
            "xsec_token=token&amp;anchorCommentId=comment&amp;type=normal"
        assertEquals("<a href=\"$htmlUrl\">@小红薯 的评论：</a>", clip.html)
    }

    @Test fun longClickKeepsFullContentAndEscapesHtml() {
        val url = buildXhsCommentUrl("note", "comment", "normal", null)
        val content = "<评论 & \"引用\">\n" + "完整内容😀".repeat(50)
        val clip = buildXhsCommentText(url, "小<红&\"薯", content)
        assertEquals("$content\n$url", clip.text)
        val htmlUrl = url.replace("&", "&amp;")
        val htmlContent = "&lt;评论 &amp; &quot;引用&quot;&gt;<br>" + "完整内容😀".repeat(50)
        assertEquals("<a href=\"$htmlUrl\">@小&lt;红&amp;&quot;薯 的评论：</a><br><blockquote>$htmlContent</blockquote>", clip.html)
    }

    @Test fun emptyContentStillCopiesLink() {
        val url = buildXhsCommentUrl("note", "comment", "normal", null)
        assertEquals(buildXhsCommentText(url, "小红薯", null), buildXhsCommentText(url, "小红薯", ""))
    }

    @Test fun missingAuthorStillProducesCommentLink() {
        val url = buildXhsCommentUrl("note", "comment", "normal", null)
        assertEquals("<a href=\"${url.replace("&", "&amp;")}\">评论：</a>",
            buildXhsCommentText(url, null, null).html)
    }
}
