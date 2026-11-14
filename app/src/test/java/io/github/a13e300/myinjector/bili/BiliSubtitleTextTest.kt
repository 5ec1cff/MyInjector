package io.github.a13e300.myinjector.bili

import org.junit.Assert.assertEquals
import org.junit.Test

class BiliSubtitleTextTest {
    @Test
    fun jsonKeepsFullContentLineBreaksAndRepeats() {
        assertEquals("你好，世界！\n重复\n重复\n第一行\n第二行 😀", biliSubtitleText("""
            {"body":[
              {"from":0,"to":1,"content":"你好，世界！"},
              {"from":1,"to":2,"content":"重复"},
              {"from":2,"to":3,"content":"重复"},
              {"from":3,"to":4,"content":"第一行\n第二行 😀"}
            ]}
        """.trimIndent(), "JSON"))
    }

    @Test
    fun jsonSupportsBomAndEmptyBody() {
        assertEquals("", biliSubtitleText("\uFEFF{\"body\":[]}", "JSON"))
        assertEquals("quoted \"text\" & <tag>", biliSubtitleText(
            """{"body":[{"content":"quoted \"text\" & <tag>"}]}""", "JSON"))
    }

    @Test
    fun assUsesEventsFormatAndKeepsTextCommas() {
        assertEquals("你好，字幕\n带,逗号\n第二行\u00a0末尾", biliSubtitleText("""
            [Script Info]
            Title: test
            [V4+ Styles]
            Format: Name, Fontname
            [Events]
            Format: Layer, Start, End, Style, Name, MarginL, MarginR, MarginV, Effect, Text
            Dialogue: 0,0:00:01.00,0:00:02.00,Default,,0,0,0,,{\b1}你好，字幕{\b0}
            Comment: 0,0:00:00.00,0:00:01.00,Default,,0,0,0,,不显示注释
            Dialogue: 0,0:00:02.00,0:00:03.00,Default,,0,0,0,,带,逗号\N第二行\h末尾
        """.trimIndent(), "ASS"))
    }

    @Test(expected = IllegalStateException::class)
    fun invalidAssIsAnErrorInsteadOfNoSubtitles() {
        biliSubtitleText("[Events]\nDialogue: broken", "ASS")
    }

    @Test(expected = IllegalStateException::class)
    fun unknownFormatIsAnError() {
        biliSubtitleText("anything", "UNKNOWN")
    }
}
