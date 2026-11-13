# 从选中消息开始查看历史消息

消息菜单的“查看历史消息”保留发送者筛选，首次只请求所选消息附近的
40 条搜索结果，并把该消息定位到结果列表顶部。向上滚动加载更新结果，
向下滚动加载更旧结果，默认不限制搜索时间范围。

## 官方客户端的搜索筛选

启用模块的“查看历史消息”后，官方 Telegram 搜索栏的日历按钮提供三个入口：

- **消息类型**：全部、图片、视频、图片和视频、防剧透图片、防剧透视频、
  防剧透图片和视频、文件、语音、圆形视频、音乐、GIF、链接、投票。
  发送者和关键词条件继续保留。切换类型时从当前结果列表
  顶部可见消息附近开始；聊天视图中则使用搜索箭头当前选中的消息，继续支持
  双向加载。当前位置不属于新类型时，显示附近符合条件的结果。
- **时间范围**：设置起止日期、单独开始或结束日期，或者清除范围。开始、结束
  日期均包含当天，按当前搜索开始时的系统时区计算，跨夏令时不按固定 24 小时处理。
- **跳到日期**：定位到该日期附近的筛选结果，继续向两侧加载。若设置了时间
  范围，日期选择器也限制在该范围内。它不会把选中日期变成新的截止时间。

这三个入口只接入官方客户端，不替换第三方客户端已有的类型筛选。
筛选对后续分页和关键词修改持续生效，退出搜索后清除。取消选择对话框不更改
现有条件。没有匹配消息时仍保留入口，可修改或清除筛选。
入口菜单、类型选择、时间范围与日期选择窗口跟随 Telegram 当前的深浅主题，
不依赖系统是否开启深色模式。每次打开窗口时重新读取主题。

## 实现

- `HistoricalNewsOption` 在调用原生发送者搜索时传入选中消息。
- `AnchoredMessageSearch` 接管消息菜单搜索及启用筛选的搜索。它复用原生搜索列表、消息对象和
  搜索通知，单独发送 `messages.search` 请求。退出搜索、切换搜索或离开会话
  时取消请求，旧响应不会写入新搜索。
- `MessageSearchWindow` 管理两个方向的游标和结束状态。初次请求使用
  `offset_id = 选中消息 ID`、`add_offset = -20`；旧页使用偏移 0，
  新页使用偏移 -40。按“会话 ID + 消息 ID”去重，迁移群分别加载两个会话。
- 插入新页后保留当前第一条可见消息的像素位置。列表更新使用完整刷新，
  不套用原生适配器只向尾部追加的更新通知。
- 每次成功加载后，在绘制前等待列表完成布局。若内容不足以滚动，自动继续
  加载较旧结果，较旧方向结束后检查较新方向，直到可以滚动或两侧都结束。
  不按固定消息条数判断是否满屏；搜索取消、发起新请求时移除待执行的检查，
  请求失败后也不自动循环重试。
- `AnchoredSearchDeobf` 静态定位适配器、列表、滚动回调和布局管理器方法，
  不依赖混淆后的名称。
- `SearchFiltersUi` 复用日历按钮，关闭功能时继续执行原生日历点击逻辑。
  窗口使用独立的 `ContextThemeWrapper`，通过 Telegram 保留的
  `AndroidUtilities.checkAndroidTheme` 选择原生浅色或深色对话框主题。
  `MessageSearchFilters` 保存消息类型、日期范围和时区，每页请求都带上
  `filter`、`min_date`、`max_date`。日期定位先用 `messages.getHistory` 的
  `offset_date` 取得附近消息 ID，再重建双向搜索窗口。
- 切换类型前读取原列表的可见位置或原生搜索选中位置，保存“会话 ID + 消息 ID”
  作为新窗口锚点。加载中或当前类型没有结果时沿用上一窗口的锚点，不跳回最新消息。
- 发送者和非“全部”的消息类型同时选中时，服务端按类型筛选，模块按消息的
  实际发送者筛选，不使用转发来源。设备实测群聊同时传 `from_id` 和媒体
  `filter` 会返回 `400 INPUT_FILTER_INVALID`，改变偏移、边界或关键词仍然失败。
  分页游标使用全部服务端结果，不受本地发送者筛选影响；整页没有匹配发送者
  时继续请求，直到找到匹配消息或该方向结束，首次无结果时也检查另一方向。
- 日期定位响应带搜索版本检查，修改条件、退出搜索或关闭会话后，旧响应
  不会把界面跳回之前的日期。
- 三种防剧透类型分别向服务端请求 `Photos`、`Video`、`PhotoVideo`，再按 `media.spoiler`
  标记本地筛选。它可与发送者、关键词和时间范围组合；文字剧透、敏感内容遮罩
  不视为媒体防剧透。已经点开过的防剧透媒体仍符合条件，分页游标继续使用全部
  服务端结果，连续没有匹配项的页面也不会提前结束搜索。

计数显示当前已加载窗口中的结果数量。选中消息若已经删除，则定位到附近
仍然存在的搜索结果；尚未发送成功、没有服务器消息 ID 的消息沿用原生搜索。

## 验证

已验证 NaGram 12.10.3（1249）、官网 Telegram 13.1.1（72379）和设备提取的
Play 版 13.1.1（72371）的成员定位和反射签名。Play 版在构造适配器时会复用
分支寄存器，因此从较早的 `setLayoutManager` 调用定位搜索列表。
桌面测试模拟完整服务端分页，覆盖 28 组双向遍历，包括首尾锚点、
不足一页、超过一页、稀疏 ID、迁移群 ID 重叠和锚点缺失。

DexKit 本地测试入口支持 `TELEGRAM_ANCHOR_ONLY=1`，只执行本功能的检查。
新增筛选入口在官方 Play 版、官网版 13.1.1 上检查成员定位及过滤器构造函数。
日期测试覆盖当天首尾秒、单侧范围、夏令时的 23/25 小时日期、日期定位边界，
并模拟发送者、图片类型、日期范围同时生效时的完整双向分页。
网络请求和手机端实际滚动效果仍需安装模块验证。
发送者与类型组合的分页回归测试覆盖连续空页、两侧遍历、迁移群和完全无匹配。

## Telegram 13.2.2（73031）初始化失败

从 ADB 设备提取 Play 版 `org.telegram.messenger` 的 base APK 和 arm64、xxhdpi split。
base APK 的 SHA-256 为 `47ebdaac21ca00b20f2872a9eebcce63f808c059f5de2fc4cd6fe3d060be1031`。
样本和验证日志保存在 `~/Documents/Codex/scratch/MyInjectorPriv/telegram-13.2.2-history/`。

报错对应 `TelegramApiDeobf.kt` 原第 188 行的 `MessageObject.getFromPeer` 规则。
dexsql 确认新版 `MessageObject` 为 `org.telegram.messenger.mj`，已没有返回
`TLRPC.Peer` 的方法，而消息字段 `mj.k:TLRPC.Message` 和 `Message.from_id:TLRPC.Peer` 仍在。
官方源码的 [getFromPeer，第 10098–10103 行](https://github.com/DrKLO/Telegram/blob/21f7d109da6ab3506c9e9577f98c6a38771ab335/TMessagesProj/src/main/java/org/telegram/messenger/MessageObject.java#L10098-L10103)
只是判空后返回 `messageOwner.from_id`，符合 R8 内联移除 getter 后的结果。

`HistoricalNewsOption` 现在通过已有字段规则解析 `messageOwner`，直接读取 `from_id`，
保留消息为空、发送者为空时不显示菜单的行为，不改用转发来源或会话 ID。
删除不再使用的 getter 规则。已有字段缓存仍有效，失败的 getter 也没有成功缓存，因此不调整缓存版本。

静态检查从 `HistoricalNewsOption`、`AnchoredMessageSearch` 和 `SearchFiltersUi`
提取运行时单独解析的 API 调用，补上原先只检查 `findHistoricalNews` 的遗漏。
补齐后可在修改生产代码前复现同一 getter 定位失败，修改后通过。
另外新增 `Anchored search list` 和 `Search controls` 两组检查，覆盖列表和筛选入口。

官方 13.2.2、官方 13.1.1（72371）和 NaGram 12.10.3（1249）均通过以下四组检查：

```sh
TMPDIR="$HOME/Documents/Codex/scratch/MyInjectorPriv/telegram-13.2.2-history" \
python3 scripts/check_telegram_apks.py \
  --feature HistoricalNewsOption --feature 'Anchored search state' \
  --feature 'Anchored search list' --feature 'Search controls' \
  "$HOME/Documents/Codex/scratch/MyInjectorPriv/telegram-13.2.2-history/telegram-13.2.2.apk" \
  ~/tg-apks/telegram-13.1.1-72371/base.apk ~/nagram-1249.apk
```

debug、release 构建和 4 项 Telegram 分页单测通过。AyuGram、exteraGram 13.0.3 的
`HistoricalNewsOption` 成员和运行时 API 检查也通过。

验证边界：AyuGram 13.0.3 的新增搜索状态和列表检查失败，属于此次扩大检查范围时发现的旧版本兼容问题，
不能据 API 检查通过认定其完整历史搜索可用。全量单测另有 `SettingsCatalogTest` 和
`PreferenceSyncTest` 两项失败，断言仍使用新增 `fixHyperOS4GreezeFCMRestrict` 之前的设置列表和数量。
本次没有在真实 Xposed 宿主中验证菜单点击和网络搜索。
