package io.github.a13e300.myinjector.bili

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BiliCommentLinkTest {
    @Test
    fun imageDynamicMatchesSharedLinkWithoutTracking() {
        assertEquals(
            "https://t.bilibili.com/415375385?comment_on=1&comment_root_id=323854887520&type=2#reply323854887520",
            biliCommentUrl(415375385, 11, 323854887520, 0)
        )
    }

    @Test
    fun secondaryReplyKeepsRootAndTarget() {
        assertEquals(
            "https://www.bilibili.com/video/av123?comment_on=1&comment_root_id=323354225632&comment_secondary_id=323354341104#reply323354341104",
            biliCommentUrl(123, 1, 323354341104, 323354225632)
        )
        assertEquals(
            "https://t.bilibili.com/123?comment_on=1&comment_root_id=456&comment_secondary_id=789#reply789",
            biliCommentUrl(123, 17, 789, 456)
        )
    }

    @Test
    fun rootDoesNotNeedSecondaryParameter() {
        assertEquals(biliCommentUrl(123, 1, 456, 0), biliCommentUrl(123, 1, 456, 456))
    }

    @Test
    fun missingDataAndUnsupportedTypesHaveNoEntry() {
        assertNull(biliCommentUrl(0, 1, 456, 0))
        assertNull(biliCommentUrl(123, 1, 0, 0))
        assertNull(biliCommentUrl(123, 1, 456, -1))
        assertNull(biliCommentUrl(123, 12, 456, 0))
    }
}
