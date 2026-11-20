# 净化搜索页面

参考 BiliRoaming「净化搜索页面」的功能范围，移除搜索页热搜、推荐内容和搜索框默认推荐词，
保留搜索历史、输入后的联想及正常搜索结果。首页搜索框的默认推荐词也一并处理。
在「MyInjector 设置 → 广告与推广」增加独立开关，JSON 键为 `purifySearch`，默认开启。
开关在后续数据读取、加载或解析时判断，无需重启，不主动刷新已打开的页面。
安装和运行错误使用现有 Hook 错误汇报，归入「净化搜索页面」。

## 原功能与本版适配

BiliRoaming 通过 Fastjson 清理旧搜索模型，并将 Java SearchMoss 的默认词响应置空，参考：

- [JsonHook.kt:279](https://github.com/yujincheng08/BiliRoaming/blob/7c792dc8fea321ff6a308ce3424668f81019df58/app/src/main/java/me/iacn/biliroaming/hook/JsonHook.kt#L279)：默认词、排行榜和推荐模型。
- [JsonHook.kt:385](https://github.com/yujincheng08/BiliRoaming/blob/7c792dc8fea321ff6a308ce3424668f81019df58/app/src/main/java/me/iacn/biliroaming/hook/JsonHook.kt#L385)：单独解析的热搜和推荐数组。
- [ProtoBufHook.kt:127](https://github.com/yujincheng08/BiliRoaming/blob/7c792dc8fea321ff6a308ce3424668f81019df58/app/src/main/java/me/iacn/biliroaming/hook/ProtoBufHook.kt#L127)：Java SearchMoss 默认词。

9.19.0 同时保留旧搜索页和 `kntr.app.search` 的 CMP 搜索页。新版发现页使用 Ktor 请求
`x/v2/search/square` 和 `x/v2/search/recommend`，默认词改用 KSearchMoss，还会解析路由及
缓存中的 Kotlinx 模型。只照搬旧 Fastjson 和 Java SearchMoss Hook 无法覆盖这些路径。
本实现根据当前 APK 的数据流独立编写，不依赖 BiliRoaming 的总开关或隐藏选项。

## 实现方式

旧版在 `SearchSquareType.getType()` 隐藏非 `history` 板块。原生解析器和发现页只为
`trending`、`recommend`、`history` 分发内容，未匹配的类型会跳过，因此不影响历史板块。
`SearchReferral` 读取列表时返回空列表，同时隐藏标题、页数，覆盖单独刷新推荐的路径。
`DefaultKeyword` 的文本及路由 getter 返回 `null`，关闭默认词的前置显示、自动刷新和动画。
不在全局 Fastjson 方法中清空任意对象，也不直接改写已有旧版模型的字段。

设备实际显示的旧版搜索页是 `BiliMainSearchActivity`，搜索框是 `OgvSearchView`。其默认词
观察回调在模型或 `show` 为 `null` 时仍会调用 `setQueryHint("")`；基础 `SearchView` 又把
非 `null` 的空字符串直接写入 EditText，覆盖原有提示，不会使用 SearchableInfo 的后备提示。
因此仅将 getter 改为 `null` 不能恢复旧版 hint。额外在 `setQueryHint()` 写入前使用宿主固定
字符串资源替换提示，严格限定在该 Activity 内的 `OgvSearchView`，不修改输入文本或其他搜索框。
普通模式使用 `search_global_string_72`，青少年模式使用 `search_global_string_201`，跟随宿主语言。
通过 DexKit 定位该观察回调并解除其内联，保证已经编译的提示更新也经过 `setQueryHint` Hook。

新版通过 DexKit 定位搜索发现仓库的三个协程方法：

- square：仅保留返回列表中的 History 板块，历史内容仍由宿主从本地记录合并。
- recommend：将独立刷新推荐的返回列表替换为空列表。
- defaultWord：将返回的搜索默认词模型替换为 `null`，让宿主使用固定搜索提示。

这三个方法首次调用可能返回 `COROUTINE_SUSPENDED`，恢复后由对应的 `invokeSuspend()`
再次调用原方法。Hook 在调用完成后按实际结果类型处理，不替换挂起标记，不手动恢复协程，
也不将正常网络异常伪装为空结果。

额外定位两个子 serializer 的 `deserialize()`：

- `kntr.app.search.model.SearchDefaultWord`：搜索页缓存和路由中的默认词。
- `tv.danmaku.bili.khomeapi.frame.SearchDefaultWord`：首页响应内的默认词。

解码完成后才替换为 `null`，覆盖嵌套对象和 NullableSerializer 包装，并让 Decoder 完整消费
原数据。首页响应中这两个默认词字段本身允许为 `null`。

新版固定提示的回退条件是默认词对象为 `null`，不会检查其中的文本是否为空。首页来源缺少
默认词时，`SearchDiscoverStateMachine` 还会自行合成非 `null` 的空模型，因此只清空请求结果
和缓存仍可能留下空白提示。额外定位 `SearchPageState` 与发现页 `Content` 的构造函数，
仅将默认词参数设为 `null`；保留输入状态、板块列表和青少年模式标记，让宿主统一选择固定提示。
普通模式使用宿主 `search_global_string_72`，文案为「搜索视频、番剧或up主」；青少年模式仍使用
原生的「搜索你感兴趣的视频」。
不改写默认词模型的构造函数，也不拦截宿主用于生成普通提示的字符串资源。
此功能不阻止搜索接口发出网络请求，不修改输入词、联想请求、历史存储或结果页数据。

关闭开关后放行后续处理；已经返回空内容的内存状态不会自动恢复，需要重新加载。

## 验证

已在 9.19.0 APK 上核对生产 DexKit 定位、三个协程的恢复调用链、两个展示状态的可空默认词参数、
固定提示回退分支、serializer 的 Decoder 结束调用，以及旧版 getter 签名。设备布局已确认当前
页面走旧版 `BiliMainSearchActivity → OgvSearchView → SearchView.QueryText`，并核对观察回调、
`setQueryHint()`、`updateQueryHint()` 与宿主提示资源。Debug、Release 编译通过。
用户已在旧版搜索页验证净化功能可用，并确认固定提示恢复正常。
