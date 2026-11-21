package io.github.a13e300.myinjector.bili

enum class BiliInterfaceMode(val key: String, val title: String, val forcedValue: Boolean?) {
    FOLLOW("follow", "不强制", null),
    NEW("new", "强制新版", true),
    OLD("old", "强制旧版", false),
}

enum class BiliInterfaceComponent(
    val key: String,
    val title: String,
    val description: String,
    val decisionKeys: List<String> = emptyList(),
    val defaultMode: BiliInterfaceMode = BiliInterfaceMode.FOLLOW,
) {
    SHARE("share", "分享框架", "迁移原「固定新版分享框架」选项", defaultMode = BiliInterfaceMode.NEW),
    VIDEO("video", "视频详情", "Theseus / Theseus2", listOf("kmp_theseus")),
    SEARCH("search", "搜索主页面", "普通搜索入口；半屏搜索仍由宿主处理", listOf("using_cmp_search_module")),
    SEARCH_SUBPAGE("searchSubpage", "搜索子页面", "番剧聚合、漫画和选集等子页面，与搜索主页面独立", listOf("using_cmp_search_module_subpage")),
    OPUS("opus", "图文详情", "原生详情 / CMP 详情", listOf("following.opus_detail_use_cmp")),
    FOLLOWING("following", "关注信息流", "底栏进入的关注信息流", listOf("following.following_list_use_cmp")),
    HISTORY("history", "历史记录", "原生历史 / CMP 历史", listOf("history.history_use_cmp")),
    SPLASH("splash", "开屏框架", "开屏净化可能阻止页面显示", listOf("dd_splash_business_use_cmp_exp")),
    MINE("mine", "我的页面", "旧版 / V2，覆盖原有设备类型限制", listOf("dd.wide_mine_v2")),
    COMMENTS("comments", "评论框架", "统一控制评论列表、路由、竖屏和横屏场景；旧版包括 comment2 / comment3",
        listOf("comment.kntr.enabled", "comment.kntr.route.enabled", "comment.kntr.story.enabled", "comment.kntr.landscape.enabled")),
    ;

    val settingKey: String get() = "interfaceVersion:$key"
}
