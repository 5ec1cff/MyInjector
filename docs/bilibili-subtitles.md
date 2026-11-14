# 视频菜单查看字幕

## 行为

视频页右上角三点菜单的第二行操作区首位增加字幕按钮：

- 有字幕：显示“查看字幕”，点击后关闭菜单并打开字幕弹窗。
- 无字幕：显示灰色“没有字幕”，点击和无障碍操作均禁用。
- 元数据加载中或失败：分别显示“字幕加载中”“字幕暂不可用”，不误报为无字幕。

弹窗显示完整正文，可以长按选择、复制部分文本，也可以点击“复制全部”。
多语言字幕可在顶部切换；复制全部复制当前语言，保留换行和重复句子。
下载失败时可重试，切换语言、关闭弹窗或销毁 Activity 时取消旧加载任务。

读取宿主已经获取的字幕元数据，不额外请求字幕列表，不改变播放器字幕开关。
仅点击查看、切换语言或重试时下载对应字幕文件，支持 JSON 和 ASS。

## 实现

入口为独立的 `BiliHandler.hookViewSubtitles`，混淆定位缓存在 `subtitle.*`。
9.19.0（9190400）中，视频页和分享框架分别有不同入口，不能只按 Activity 判断。

设备上的 `com.bilibili.ship.theseus.detail.UnitedBizDetailsActivity` 使用工具栏 `MenuService`。
三点菜单中的分享渠道与第二行操作是两份列表。向分享框架增加第二行，并不会增加到实际显示的操作行。
此前仅覆盖 Theseus2 的分享回调、后来只补视频下方分享回调，都会漏掉此入口；hook 安装成功不代表菜单已经插入。

工具栏路径由 `BiliSubtitleToolbar` 处理：

| 功能 | 宿主成员 |
| --- | --- |
| 工具栏 | `com.bilibili.ship.theseus.united.page.toolbar.MenuService` |
| 操作列表组装 | `MenuService$createSettingGroup$2.invokeSuspend(Object)` |
| 水平操作行 | `VideoSettingButtonComponent(List)` |
| 按钮数据 | `gyo2`，即 `VideoSettingButton` |
| 按钮渲染 | `VideoSettingButtonComponent.a(gyo2, Composer, int)` |
| 点击对象 | `eyo2.invoke()` |
| 当前播放器 | `MenuService.n:PlayerContainer` |
| 字幕元数据 | `InteractLayerService.getSubtitleReply():SubtitleViewReply` |

仅在工具栏组装操作列表的调用范围内，复制第一份水平操作行的列表并在首位加入按钮。
其他按钮、其他使用同一组件的页面均不改动。
使用所属播放器的 `IPlayerSettingService` 关联字幕服务，服务启动或字幕回复更新时刷新关联，
不会绑定、停止播放器服务，也不使用全局“最近播放视频”的字幕缓存。

宿主把按钮的点击字段收窄为具体 lambda 类，因此创建独立实例作为标记，在其 `invoke` 前处理字幕操作。
不使用宿主按钮的点击或曝光事件，不发送宿主按钮的统计。
按钮禁用时，仅在该项渲染范围内将 `clickableNoInteraction$default` 的 enabled 设为 false，
清除默认参数位，并添加 0.38 alpha。

CC 图标生成到应用自己的 `cache/myinjector_subtitle_icon.png`，交给宿主图片加载器读取本地路径。
宿主会按主题给图标着色，无需联网下载图标。不能使用 data URL：宿主将其当成本地文件名，导致图标为空。

Theseus2 分享路径保留原有 `UgcKingPositionService$createSharePanel$callback$1` 接入：
通过共享的播放区域仓库关联 `TheseusSubtitleService`，检查 loading/error 后读取 Kotlin protobuf 元数据。
在菜单列表回调中复制列表、插入第二行首位，并为新增按钮补充宿主渠道过滤允许项。
宿主可能复制菜单项，因此点击状态绑定到回调，不依赖菜单项身份。
此路径已通过真实 APK 对象探针验证，未在当前设备的视频页上完成界面验证。

两条路径共用系统 `AlertDialog`、可选择文字的 `TextView` 和 `ScrollView`。
HTTP 下载在 IO 协程执行；JSON 读取 `body[].content`，ASS 读取 Events 段的 Format/Dialogue、
去掉样式标签并还原 `\N`、`\n`、`\h`，与样本中 SsaParser 的文字处理一致。

## 验证

专项测试共 25 项：24 项通过，1 项因缺少 9.17.0 样本跳过，Debug APK 构建通过。
覆盖正文解析、JSON/ASS 格式，以及真实 9.19.0 APK 的字幕、工具栏操作行、菜单与过滤入口定位。
JVM 的 JSON 测试使用 test-only 的 `org.json`，生产使用 Android 自带 API。

设备上已实际打开上述 Theseus Activity 的右上角菜单并命中工具栏操作行。
用户确认按钮可见，有字幕的视频可以打开字幕弹窗，本地 PNG 图标修正后功能可用。
图标初次使用 data URL 加载失败，已按真实错误日志改为本地 PNG 路径。
日志 `bili subtitle toolbar inserted` 包含字幕数量与按钮文案，便于区分 hook 安装与操作行插入。
