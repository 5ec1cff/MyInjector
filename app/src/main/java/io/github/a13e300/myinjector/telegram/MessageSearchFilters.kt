package io.github.a13e300.myinjector.telegram

import java.time.LocalDate
import java.time.ZoneId

internal enum class SearchMessageType(val title: String, val filter: String, val spoilerOnly: Boolean = false) {
    ALL("全部消息", "Empty"), PHOTOS("图片", "Photos"), VIDEO("视频", "Video"),
    MEDIA("图片和视频", "PhotoVideo"), SPOILER_PHOTOS("防剧透图片", "Photos", true),
    SPOILER_VIDEO("防剧透视频", "Video", true), SPOILER_MEDIA("防剧透图片和视频", "PhotoVideo", true),
    FILES("文件", "Document"), VOICE("语音", "Voice"),
    ROUND_VIDEO("圆形视频", "RoundVideo"), MUSIC("音乐", "Music"), GIF("GIF", "Gif"),
    LINKS("链接", "Url"), POLLS("投票", "Poll"),
}

internal data class SearchDateRange(val start: LocalDate? = null, val end: LocalDate? = null) {
    init { require(start == null || end == null || !end.isBefore(start)) }

    fun lower(zone: ZoneId): Long? = start?.atStartOfDay(zone)?.toEpochSecond()
    fun upper(zone: ZoneId): Long? = end?.plusDays(1)?.atStartOfDay(zone)?.toEpochSecond()
    fun contains(seconds: Int, zone: ZoneId): Boolean =
        (lower(zone)?.let { seconds >= it } ?: true) && (upper(zone)?.let { seconds < it } ?: true)

    val title: String get() = if (start == null && end == null) "不限时间"
        else "${start ?: "不限"} 至 ${end ?: "不限"}"
}

internal data class MessageSearchFilters(
    val type: SearchMessageType = SearchMessageType.ALL,
    val dates: SearchDateRange = SearchDateRange(),
    val zone: ZoneId = ZoneId.systemDefault(),
) {
    val active: Boolean get() = type != SearchMessageType.ALL || dates.start != null || dates.end != null
    val minDate: Int get() = dates.lower(zone)?.minus(1)?.coerceAtLeast(0)?.toInt() ?: 0
    val maxDate: Int get() = dates.upper(zone)?.toInt() ?: 0
    fun jumpTime(date: LocalDate): Int {
        val seconds = date.atStartOfDay(zone).toEpochSecond()
        return seconds.coerceAtLeast(dates.lower(zone) ?: 0)
            .coerceAtMost(dates.upper(zone)?.minus(1) ?: Int.MAX_VALUE.toLong()).toInt()
    }
}
