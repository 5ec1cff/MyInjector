package io.github.a13e300.myinjector.bili

import org.json.JSONObject

internal data class BiliSubtitleTrack(val title: String, val url: String, val format: String)

internal fun biliSubtitleText(source: String, format: String): String {
    val text = source.removePrefix("\uFEFF")
    return when (format) {
        "JSON" -> {
            val body = JSONObject(text).getJSONArray("body")
            (0 until body.length()).map { body.getJSONObject(it).getString("content") }
                .filter(String::isNotBlank).joinToString("\n")
        }
        "ASS" -> {
            var inEvents = false
            var columns = emptyList<String>()
            val lines = mutableListOf<String>()
            for (line in text.lineSequence()) {
                val entry = line.trim()
                when {
                    entry.startsWith('[') -> inEvents = entry.equals("[Events]", ignoreCase = true)
                    !inEvents -> Unit
                    entry.startsWith("Format:", ignoreCase = true) ->
                        columns = entry.substringAfter(':').split(',').map { it.trim().lowercase() }
                    entry.startsWith("Dialogue:", ignoreCase = true) -> {
                        check(columns.lastOrNull() == "text") { "字幕格式不支持：ASS Text 列不在末尾" }
                        val fields = entry.substringAfter(':').split(',', limit = columns.size)
                        check(fields.size == columns.size) { "ASS 字幕行不完整" }
                        // 与宿主 SsaParser 一样，去掉样式标签并还原 ASS 换行和空格。
                        val content = Regex("\\{[^}]*}").replace(fields.last(), "")
                            .replace("\\N", "\n").replace("\\n", "\n").replace("\\h", "\u00a0")
                        if (content.isNotBlank()) lines += content
                    }
                }
            }
            check(inEvents || lines.isNotEmpty()) { "ASS 字幕缺少 Events 段" }
            lines.joinToString("\n")
        }
        else -> error("字幕格式不支持：$format")
    }
}
