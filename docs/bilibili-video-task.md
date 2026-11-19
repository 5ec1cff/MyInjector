# 多任务视频播放

在视频右上角三点菜单的第二行操作中增加「在新任务打开」，排在「查看字幕」「查看封面」之后。
首页视频与图文动态、视频底部推荐和视频搜索结果的长按菜单也增加同名按钮。
底栏「关注」信息流中，视频与图文卡片右上角三点菜单也增加此按钮，排在第一项。
图文详情页顶部也增加仅显示图标的入口，新版放在分享按钮左侧。
开关位于「MyInjector 设置 → 多任务视频播放」，JSON 键为 `openVideoInNewTask`，默认开启，
切换后对下一次构建的菜单生效。安装和点击错误进入现有 Hook 错误汇报。

点击详情页按钮后先关闭菜单，再复制当前 Activity 的启动 Intent，显式指定原 Activity 类，保留 data、extras 等
启动参数，将 flags 设置为 `FLAG_ACTIVITY_NEW_DOCUMENT | FLAG_ACTIVITY_MULTIPLE_TASK`。
提交新页面的启动请求后调用旧 Activity 的 `finish()`；启动请求抛异常时保留旧页面并记录错误。
新页面仍保留此按钮，重复点击会再次重新打开到新任务。

这是重新创建详情页，不迁移已有 Activity、播放器实例，也不额外读取或恢复当前播放进度、
分 P 和评论滚动位置。播放内容由复制的启动参数与宿主恢复逻辑决定。
本操作不额外启动后台播放、首页自动播放或小窗。

列表入口从长按卡片读取视频 ID，通过宿主 `BLRouter` 的 `RequestMode.INTENT` 生成详情页启动
Intent，再设置相同的文档任务 flags 和标记，保留原列表页面。不会误用当前正在播放的视频 ID。
普通视频 `av` 与竖屏视频 `vertical_av` 都使用 `bilibili://video/{id}`，不携带卡片原 Story 地址的
强制竖屏参数。此入口固定打开普通视频详情页，不依赖「替换竖版视频」开关，也不改变画面比例。
首页图文动态 `picture` 使用卡片的原始图文详情路由，支持 `opus/detail`、`following/detail` 及
对应的网页原链接，保留 query 和 fragment。不把图文 param 当成视频 ID，不请求短链接。
9.19.0 的图文拦截器会按 `following.opus_detail_use_cmp` 灰度重定向到 `opus/cmp/detail`，
这类页面路由无法通过 `RequestMode.INTENT` 返回 Android Intent。图文新任务入口改用
`BLRouter.findRoutes()` 匹配原始地址，仅接受宿主 `OpusDetailFragment` 或 `DynamicDetailFragment`。
调用宿主 `AbstractLauncherKt.createExtrasForFragment()` 保留路径变量、query、props 与原始 URL，
沿用宿主 Fragment Launcher 的 `fragment`、`fragment_args` 协议，在 `ComposeActivity` 中打开。
这个入口使用宿主保留的 Fragment 详情页，不执行 CMP 重定向，也不改变普通点击的灰度配置。
图文同样创建文档任务，但不写入独立视频任务标记，不参与后台播放豁免。
首页与底部推荐的反馈面板在原生模型中加入操作项，底部推荐旧菜单沿用原生菜单服务。
首页还会直接构建 `ListThreePointMenuDialogFragment`，不经过 NF 反馈回调；这条路径在新旧卡片
的长按入口捕获视频，并在原生菜单列表中插入 `ImageTitleData` 操作行，复用原生关闭逻辑。
该适配器只读取图标 URL，使用本地生成的明暗两套图标，并用 `file://` URI 传给图片加载器。
裸文件路径经过 `ImageRequestBuilder.url(String)` 时只会被 `Uri.parse()` 解析，缺少 scheme，
不能作为本地图片加载；宿主 `BiliImageLoaderHelper.fileToUri()` 也使用 `file://` 格式。
不传仅用于其他面板的 Drawable。
首页回调按字段声明类型读取视频数据，避免把同样实现 `BasePegasusData` 的 `DislikeItemData`
菜单项误认成视频，导致 Hook 安装中断。
旧卡片的 `uri` 字段是私有成员，通过公开的 `getUri()` 读取图文地址。
底部推荐同时适配 Theseus 和 Theseus2 的普通菜单与异步 NF 反馈菜单。
搜索结果增加原生按钮组，按配置对象和自定义菜单项关联视频，支持 Compose 子行单独重组；
自定义组直接关闭面板并打开视频，不提交负反馈，也不弹出原生反馈成功提示。
只处理有明确视频 ID 的普通、竖屏视频，以及有上述详情路由的图文动态，不处理直播、番剧。

关注信息流当前使用旧版动态卡片菜单。通过 DexKit 定位菜单入口、适配器与点击分发方法，在
构建菜单时关联所选卡片，插入自定义项，并只替换该项的图标和文案渲染。其他原生菜单项照常
渲染和处理。点击自定义项时跳过原生业务分发，外层回调仍负责关闭手机面板或宽屏弹出菜单。
原生 `DynamicType.av`、`story` 从视频模块读取 aid，固定打开普通详情页；`word`、`draw` 从
当前卡片读取动态 ID，生成 `bilibili://opus/detail/{id}?opus_type=dyn`，使用已验证的图文独立容器。
不把动态 ID 当成 aid，不触发关注、分享、举报等其他菜单操作，也不增加网络请求。
新入口共用 `openVideoInNewTask`，下次打开菜单生效，错误归入此开关的 Hook 错误汇报。

### 图文详情顶部入口

设备实际打开的是 `kntr.common.compose.launcher.VertexActivity` 中的 CMP 图文页。
通过 DexKit 定位 `OpusDetailNavBar` 的按钮行，以及图片头图使用的 `OpusMaskedActionPill`。
只在这两处绘制分享图标时，在原分享按钮前插入图标按钮，复用对应的原生普通或胶囊按钮样式。
普通按钮之间保留 20dp 间距，图标复用已有新任务图标，颜色由宿主按钮处理，提供无障碍文案。
胶囊按钮栏可能独立重组，因此也单独维护调用作用域。新增内容使用独立的 Compose 分组，
不改变原分享按钮的状态和业务回调。

`VertexActivity.onCreate()` 从 Intent 的 `kntr:vertex_source` 读取 `RouteVertexSource`，后者
保存完整路由字符串，重新调用宿主 Router 查找页面，不是只能消费一次的页面实例。
因此顶部按钮复制原 Intent、保留这个 Parcelable，并显式指定当前 Activity 类，使用
`NEW_DOCUMENT | MULTIPLE_TASK` 重新打开同一图文；启动成功后再 `finish()` 原页。
这条入口保持 CMP 页面，不经过列表图文入口使用的 Fragment 路由，也不请求短链接。

原生 `OpusDetailFragment` 顶栏只有更多按钮，在其左侧增加相同图标，并相应调整标题、关注
按钮等指向更多按钮的布局约束。重开时保留 `ComposeActivity` 的 `fragment` 与 `fragment_args`。
两种入口共用 `openVideoInNewTask`，新页面生效，不给图文任务写入视频后台播放标记。
新增入口已核对 9.19.0 APK 的混淆定位和启动参数，Debug、Release 编译通过，用户已安装验证功能正常。

## 独立任务后台播放

用户测试发现：宿主开启后台播放后，离开哔哩哔哩可以继续播放，但切入 App 内其他任务会暂停，
系统媒体控制也不能恢复。9.19.0 的 `BackgroundPlayService` 使用全局顶部 Activity 和应用可见性
识别页面切换，不区分 task，在这两条路径持有 `DisablePlayLock` 并暂停；原视频页恢复时才释放锁。

新增「多任务视频播放 → 独立任务后台播放」，JSON 键为 `newTaskBackgroundPlay`，默认开启，
修改后对后续跨页面回调生效。它与菜单入口开关独立，错误归入自己的 Hook 错误汇报。

通过本版「在新任务打开」创建的任务会在根 Intent 中写入专用 category 标记。豁免必须同时满足：

- 原视频 Activity 所属 task 的根 Intent 带有该标记，且 Activity 没有销毁或正在结束。
- 该播放器的 `BackgroundPlayService.isEnable()` 返回 true，即宿主后台播放可用且已开启。
- 哔哩哔哩自身可见，当前顶部是同一应用内另一个 task 的 Activity。
- 原生执行流已进入顶部 Activity 观察或 IPC 可见性观察这两个回调的保留播放器判断。

实现通过 DexKit 定位两个观察回调、各自的后台播放服务字段及原生保留判断。在回调执行期间，
仅对满足条件的同一服务放行已有的保留分支，避免暂停、加禁播锁和清空后台播放状态。
不跳过整个可见性回调，其他计数维护仍由宿主执行。调用范围使用线程局部栈，异常、嵌套调用及
开关中途变化时均成对清理；不修改宿主其他位置的边看边玩状态。

不拦截全局 `pause()`、`resume()` 或禁播锁，不绕过流量限制等其他播放规则。
在独立任务内从推荐视频继续打开的新详情页，也按相同 task ID 参与豁免，无需在每个页面传递标记。
通过 `ActivityManager.getAppTasks()` 查询当前应用的任务；Android 10 起读取 `taskId`，旧版读取 `id`。
根 Intent 使用 category 而非 extra，是因为系统生成任务快照时会用 `cloneFilter()` 清理 extra，
而 category 属于过滤字段，会继续保留。源码依据：
[Task.fillTaskInfo](https://android.googlesource.com/platform/frameworks/base/+/964d297792e5e044bc57c226ec5ecb1f736702e9/services/core/java/com/android/server/wm/Task.java)、
[Intent.cloneFilter](https://android.googlesource.com/platform/frameworks/base/+/master/core/java/android/content/Intent.java)。

同一 task 内页面之间切换时仍按宿主规则暂停前一个视频；不属于标记任务的视频页，以及 App 整体
进入后台时保持原生行为。
也不主动释放此前已经持有的锁；切换开关前已被暂停时，需要先回原视频页让宿主恢复。

安装本版后需要重新使用「在新任务打开」创建视频任务，旧版使用的 extra 标记不会作为任务标记。
可先验证视频任务 → 首页、视频任务 → 桌面 → 首页、系统媒体控制与返回原视频页；
还需验证在同一独立任务内打开推荐视频后再切回首页。其他视频实际开始播放时的播放器抢占与
音频焦点策略，仍由宿主处理。
