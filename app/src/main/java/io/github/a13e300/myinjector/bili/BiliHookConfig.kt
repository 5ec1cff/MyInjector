package io.github.a13e300.myinjector.bili

import org.json.JSONObject

enum class BiliFeature(val key: String, val title: String, val summary: String, val group: String) {
    BETTER_SHARE("betterShare", "复制原始分享链接", "新版分享在本地生成视频、动态原链接，保留标题和图文内容；旧版替换分享文本中的短链接", "分享与评论"),
    FORCE_NEW_SHARE("forceNewShare", "固定新版分享框架", "覆盖灰度开关，使用新版分享框架", "分享与评论"),
    COPY_COMMENT_LINK("copyCommentLink", "复制评论链接", "在评论长按菜单增加复制直达评论链接的操作，不请求短链接", "分享与评论"),
    COMMENT_LINK_IN_VIDEO("commentLinkInVideo", "在视频中定位评论", "打开带 #reply 的视频评论链接时保留视频页面，避免进入独立评论页", "分享与评论"),
    SHOW_AV("showAv", "显示 AV 号", "视频详情用 AV 号替代 BV 号，切换后对新打开的页面生效", "视频工具"),
    REPLACE_STORY_VIDEO("replaceStoryVideo", "替换竖版视频", "带稿件 ID 的 Story 视频使用普通详情页打开，并关闭自动进入 Story；下一次跳转生效", "视频工具"),
    VIEW_SUBTITLES("viewSubtitles", "查看字幕", "视频三点菜单增加字幕弹窗，支持选择文字和复制全部字幕", "视频工具"),
    VIEW_COVER("viewCover", "查看封面", "视频三点菜单增加封面按钮，使用内置图片查看器，支持保存", "视频工具"),
    OPEN_VIDEO_IN_NEW_TASK("openVideoInNewTask", "在新任务打开", "视频和图文详情、首页与关注信息流、底部推荐、视频搜索增加入口；竖屏视频使用普通详情页打开", "多任务视频播放"),
    NEW_TASK_BACKGROUND_PLAY("newTaskBackgroundPlay", "独立任务后台播放", "宿主开启后台播放时，独立视频任务中后续打开的视频切到 App 内其他任务后也能继续播放", "多任务视频播放"),
    PURIFY_SEARCH("purifySearch", "净化搜索页面", "移除热搜、推荐及搜索框默认推荐词，保留历史和输入联想；后续加载生效，无需重启", "广告与推广"),
    REMOVE_UNDER_PLAYER_AD("removeUnderPlayerAd", "移除视频下方广告", "阻止播放器下方的广告渲染", "广告与推广"),
    REMOVE_PAUSE_AD("removePauseAd", "移除暂停广告", "阻止暂停时的广告倒计时提示及后续广告", "广告与推广"),
    REMOVE_FREE_DATA_GUIDE("removeFreeDataGuide", "移除免流套餐推广", "保留普通流量警告和已开通免流的状态提示", "广告与推广"),
    REMOVE_CLOUD_TV_OVERLAY("removeCloudTvOverlay", "移除云视听推广角标", "移除视频开头的「云视听小电视」推广", "广告与推广"),
    REMOVE_SPLASH_AD("removeSplashAd", "移除开屏广告", "处理冷启动和热启动开屏广告", "广告与推广"),
}

/** 默认保持原有功能全部开启，新增或无效的配置项使用默认值。 */
data class BiliHookConfig(val enabledFeatures: Set<BiliFeature> = BiliFeature.entries.toSet()) {
    fun isEnabled(feature: BiliFeature): Boolean = feature in enabledFeatures

    fun withFeature(feature: BiliFeature, enabled: Boolean): BiliHookConfig =
        copy(enabledFeatures = if (enabled) enabledFeatures + feature else enabledFeatures - feature)

    fun toJson(): JSONObject = JSONObject().apply {
        BiliFeature.entries.forEach { put(it.key, isEnabled(it)) }
    }

    companion object {
        fun readFromJson(obj: JSONObject): BiliHookConfig =
            BiliHookConfig(BiliFeature.entries.filterTo(linkedSetOf()) { obj.optBoolean(it.key, true) })
    }
}
