package io.github.a13e300.myinjector.bili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiliCommentRouteTest {
    @Test
    fun rootCommentKeepsVideoAndQuery() {
        val url = "https://www.bilibili.com/video/BV1s8WJ6ZEhQ?comment_on=1&comment_root_id=323354225632&share_tag=s_i&unique_k=ZYvkIFZ"
        assertEquals(url, biliVideoCommentRouteUrl("$url#reply323354225632", "323354225632", null))
        assertEquals(
            "https://www.bilibili.com/video/av123?comment_on=1&comment_root_id=456",
            biliVideoCommentRouteUrl(biliCommentUrl(123, 1, 456, 0)!!, "456", null)
        )
    }

    @Test
    fun secondaryCommentKeepsTargetAndEncodedParameters() {
        val url = "https://www.bilibili.com/video/av123/?p=2&comment_on=1&comment_root_id=456&comment_secondary_id=789&spm_id_from=a%2Fb%23c"
        assertEquals(url, biliVideoCommentRouteUrl("$url#reply789", "456", "789"))
    }

    @Test
    fun locationCanBeStoredOutsideTargetUri() {
        assertEquals("https://www.bilibili.com/video/av123",
            biliVideoCommentRouteUrl("https://www.bilibili.com/video/av123#reply789", "456", "789"))
    }

    @Test
    fun otherLinksKeepOriginalBehavior() {
        for (url in listOf(
            "https://t.bilibili.com/123?comment_root_id=456#reply456",
            "https://www.bilibili.com/opus/123?comment_root_id=456#reply456",
            "https://example.com/video/av123?comment_root_id=456#reply456",
            "https://www.bilibili.com/video/av123?comment_root_id=456#other456",
            "https://www.bilibili.com/video/av123?comment_root_id=456",
            "bilibili://comment/detail/1/123/456",
            "not a URI",
        )) assertNull(url, biliVideoCommentRouteUrl(url, "456", null))
    }

    @Test
    fun missingOrConflictingLocationIsNotRewritten() {
        for ((root, secondary) in listOf(
            null to null, "0" to null, "-1" to null, "abc" to null,
            "99999999999999999999999" to null, "123" to null,
            "456" to "0", "456" to "789",
        )) assertNull("root=$root secondary=$secondary",
            biliVideoCommentRouteUrl("https://www.bilibili.com/video/av123#reply456", root, secondary))
    }
}
