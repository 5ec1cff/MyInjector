# 应用商店界面精简

## 样本、配置与范围

样本为当前设备重新提取的 `com.xiaomi.market` `4.129.s.00`（`40007700`）。
SHA-256 为 `bb35fb49833309548a36ae1528a047794fca49680eda132f4a0d9f30a5c0b80b`。
APK 与定向反编译位于原工作目录 `analysis/market-4.129/`，不提交 APK。

三个开关“移除开屏广告”“超级极简模式”“简化应用升级页面”均放在模块 Activity 的应用商店设置页，
存储在模块 `market` 组 remote prefs，默认开启，分别对应
`marketRemoveSplashAd`、`marketSuperMinimalMode`、`marketSimplifyUpdates`。
配置缺失项也按开启解析，不写入宿主偏好文件。界面结构修改在重启应用商店后生效。
开屏广告的分析与失败提醒机制见 [开屏广告分析](xiaomi-market-splash-ad.md)。

## 超级极简模式

仅在商店原有极简模式（`ElderChecker.INSTANCE.isElderMode()`）开启时生效。
不强制商店或系统进入极简模式，不改变其他模式的主页。

`PageConfig.updateTabs` 生成底栏数据；主页 `getPageConfig` 也可能返回缓存的配置。
在两个出口通过商店自身 `TabTag.isNativeMineTab` 识别并保留原生“我的”项的副本，修正默认选中及返回页面，
由原有 `DoubleTabProxyActivityWrapper` 创建和管理唯一的“我的”页。
旧双页实例状态的索引在 Bundle 副本中修正，原始保存状态不被改写。

创建页面后隐藏底栏及其容器，内容底部避让系统导航栏，去掉原来为底栏预留的额外空间。
配置更换或退出极简模式时，按实例恢复原始页签；模块卸载时还原配置并重建受影响主页。

搜索复用宿主 `main_search_container` 布局。先完整 inflate，让 `MainSearchView` 执行其原生
ViewStub、主题、文字与监听器初始化，再将这个控件放入“我的”原生标题栏的空白区域，
保留右侧设置和下载入口。搜索跳转、搜索页版本选择、深浅色与极简样式仍由商店处理。
标题栏高度和滚动内容顶部避让同步调整，不重写搜索 UI 或启动 Intent。

每个功能独立安装。结构不匹配或运行回调异常时写日志，并在前台按会话 Toast 提醒；
关闭的功能不显示失败 Toast。应用方法的反优化交给框架处理。

## 简化应用升级页面

新版 APK 内含旧 `UpdateListFragment` / `UpdateListRvAdapter` 和新 `UpdateListV2Fragment` /
`UpdateComponentManager` 两套升级页。保留原有 A/B 选择，不改变本地升级数据源或排序实验。

旧页通过原生 `AdSwitch.isUpdateListRecommendSupported`、`isUpdateGridRecommendSupported`、
`isUpdateAppRelatedRecommendSupported` 控制“精选推荐”“大家还安装了”等推荐请求。
这些资格返回 false，并阻止迟到的推荐回调重新写入 Adapter。
折叠资格关闭，更新数据和修改折叠状态时统一使用原生 `PageCollapseState.Expand`，
同时隐藏果园推广入口。原生升级按钮、更新状态、上下文菜单与忽略记录保持可用。

旧页的 `isCollapseState()` 虽然名称包含 Collapse，实际只在 `Expand` 时返回 true
（`WhenMappings` 把 `Expand` 映射为 1）。`updateData()` 使用该返回值决定是否保留完整数据，
并在完整展开时把 `foldButtonVisible` 设为 false。
初版错误地将此方法强制返回 false，导致列表仍被截取、展开按钮仍显示，点击后也无法展开。
修复后不再 Hook 此判断，仅在数据更新前设置原生 `Expand` 状态，让原生逻辑同时处理完整列表与按钮。

新页的 `recommend/upgradepage` 响应与本地升级列表由不同数据路径产生。
`onRefreshPageData` 接收推荐组件，将其记录到 `mRecommendComponentList`，然后合并或追加到列表。
本功能在该出口清空推荐组件，并重新用原生 `buildComponentList()` 构建升级页，
也处理请求缓存与网络回包，防止推荐内容残留。该页原生 `UIConfig(false)` 已关闭加载更多，
不额外改动分页协议。

`UpdateComponentManager` 生成升级项前强制使用 `Expand`，不会再截取前两个或前三个应用，
也不会生成展开按钮。只移除推广组件及自动更新引导卡，保留升级列表、加载/空态、
存储不足原因提示和忽略更新记录。单个升级、全部升级、通知入口及升级进度仍由原生控制器处理。

该开关与超级极简模式独立，所有模式的应用升级页都可生效；重新进入升级页后应用布局变化。

## 验证

使用 DexSQL 核对页签初始化、片段选择、原生搜索初始化和布局资源，JADX 只作辅助阅读。
模块配置测试覆盖默认开启及远端关闭值，并检查 UI、作用域和同步配置键一致。
release Kotlin 编译及 JVM 测试通过，完整真机布局、搜索点击、旋转与模式切换仍需验收。

原始字节码依据位于 `research/market-4.129/`：

- `MineTitleBar-onFinishInflate.smali`、`TabInfo-getNativeMineFragment.smali`：原生我的页与标题栏初始化。
- `AdSwitch-isUpdateListRecommendSupported.smali`：旧升级页推荐资格。
- `UpdateListRvAdapter-isCollapseState.smali`、`UpdateListRvAdapter-WhenMappings.smali`：旧页状态判断实际在 Expand 时返回 true。
- `UpdateListRvAdapter-updateData.smali`：完整数据与截断数据的选择，以及展开按钮显示条件。
- `UpdateComponentManager-buildFoldUpdateApps.smali`：新页按折叠状态截取前两项或前三项。
- `UpdateListV2Fragment-onRefreshPageData.smali`：新页合并推荐组件的入口。
