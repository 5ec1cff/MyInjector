# 设置界面迁移

## 范围与设计

参考 mpvRx 的 Material 3 Expressive：使用 Jetpack Compose、Material 3 的主题、形状、开关、卡片、按钮、搜索输入和动效，跟随系统明暗与动态配色。只迁移模块 Activity、模块注入的设置和相关弹窗；宿主原有窗口、悬浮按钮、系统窗口及 Hook 行为不在范围内。

保留现有配置键、默认值、存储格式、提交广播、热更新与应用内设置入口。首页按系统组件和已安装应用组织，组件进入独立设置页，应用继续进入对应宿主的注入设置。

## 阶段与提交

1. **基础**：接入 Compose / Material 3 Expressive，建立共享主题与独立设置模型，为后续移除 `android.preference` 做准备。
2. **Activity**：采用 Navigation 3 和应用菜单，迁移系统组件设置，删除旧 Preference XML；复用规则编辑、剪切板选择与热更新的业务操作。
3. **注入弹窗**：设置模型与 Compose 组件替代废弃 Preference，保留搜索、联动、错误与重启操作。为普通宿主 Activity 安装 AndroidX 生命周期和保存状态 owner，并在弹窗关闭时释放。
4. **验证与收尾**：检查功能对照、执行模型测试与 release 构建，并在本地模拟器检查界面、明暗主题、导航及兼容性。

每个阶段对应一个 Git 提交。旧 View 编辑器通过 Compose 的 AndroidView 互操作复用，避免重写业务实现。

## 实现与功能对照

- `InjectorTheme`：MaterialExpressiveTheme、系统动态配色、Android 8–11 的紫色回退色板，以及系统明暗模式和字体缩放。
- `SettingsApp`：Navigation 3 保存页面栈；首页显示系统框架、系统界面、小米桌面与已安装的受支持应用，应用入口仍发送原来的 SHOW_SETTINGS Intent。
- `SettingsCatalog`：原模块页面的 25 个配置／操作键、默认值及规则帮助均保留，移除 Preference XML。
- `SettingsModel` / `InjectedSettingsPanel`：Telegram 42 项、小红书 7 项、浏览器 2 项设置／操作，保留分组、搜索高亮、业务拒绝写入回滚和从属开关联动。沿用各宿主原来的持久化回调。
- `InjectedDialogUi`：使用 AndroidX ComponentDialog 提供生命周期和保存状态 owner，Compose Material 3 提供弹窗、按钮与文本输入。模块资源 Context 显式关联宿主 Activity；宿主销毁时关闭窗口并释放 Composition。
- 规则增删和说明、剪切板应用搜索／选择／保存、热更新应用筛选／选择／全部更新、错误详情／分享／复制、重启与小红书热补丁检查／清理继续可用。原配置提交函数及热更新业务函数与迁移前逐段对比一致。
- `getPreferenceScreen` 在手机管家 Hook 中仍用于访问宿主自己的界面，属于原有宿主逻辑；模块本身不再导入或实例化 `android.preference`。

## 验证

```sh
./gradlew :app:assembleDebug :app:assembleRelease :app:testDebugUnitTest
python3 scripts/check_settings_ui.py --serial emulator-5554
```

4 个单元测试验证配置键／默认值、搜索恢复、禁用／拒绝写入及从属开关联动。UI 脚本验证模块页面导航、开关持久化、规则与剪切板入口、普通 Activity 内的注入界面、嵌套错误弹窗、搜索及键盘提交。`DialogTestActivity` 仅存在于 debug，不包含在 release 中。

已在 Android 15 模拟器运行完整 UI 回归，并检查深色模式、1.5 倍字体、横屏弹窗和 Activity 旋转后的页面恢复。Android 8.0（API 26）也通过完整 UI 回归；旧模拟器仅支持 x86，因此使用移除 native 库并重新签名的临时 debug APK 验证 Java/Kotlin 界面，不代表验证了原生 Hook。截图位于 `app/build/reports/ui-smoke/` 和 `app/build/reports/ui-smoke-api26/`。

`lintDebug` 仍报告 3 个迁移前已存在的错误：`SettingsWifiDetails.kt` 的 MissingPermission / NewApi，以及 `SystemUIHandler.kt` 的 SuspiciousIndentation。这两个文件不在本次修改中；新增界面代码未出现 Lint 错误。

验证边界：模拟器验证了界面、存储和普通宿主生命周期；未在真实启用 Xposed 的 Telegram、小红书、Chrome 进程内执行 Hook、热更新或重启操作。

## 注入资源 Context 的兼容性修复

303 版在小红书宿主打开设置时出现 `Context.registerComponentCallbacks` 空指针。对应 release mapping 将混淆堆栈还原为 Compose 的 `ComposeViewContext.startObserving`。注入时加载模块资源的 LoadedApk 未创建 Application，`ContextImpl.getApplicationContext()` 返回 null；普通 Activity 的初版测试没有覆盖这个条件。

新增 `InjectedDialogContext` 统一承载设置和视频链接弹窗：保留模块资源与主题，Application、配置回调注册／注销及窗口服务委托给宿主。必须同时覆盖注册与注销方法，因为 ContextWrapper 会直接把它们转发到底层 Context，仅覆盖 `getApplicationContext()` 不能解决此问题。

回归使用 `createPackageContext` 加载其他已安装包的资源，并断言它没有 Application；随后使用模块资源绘制弹窗。修复前在 Android 15 模拟器复现出相同空指针，修复后通过以下检查（包括联动、拒绝写入、嵌套弹窗、搜索与文本输入），并在弹窗关闭及再次打开／关闭后确认宿主配置回调计数回到零：

```sh
python3 scripts/check_settings_ui.py --serial emulator-5554 --dialogs-only \
  --resource-context-package com.android.chrome --out app/build/reports/context-regression
```

该参数仅加载资源，不启动 Chrome，也不需要 Xposed；debug 测试 Activity 与这些测试入口仍不进入 release APK。

修复后 debug、release 构建和 4 个单元测试通过。真实启用 Xposed 的小红书宿主仍需安装修复版后验证。

## 首页服务连接状态刷新

Navigation 3 1.1.7 按返回栈缓存 NavEntry，原首页闭包捕获了首次传入的连接布尔值。服务回调虽然触发了外层重组，缓存入口仍读取旧值。首页改用 Compose 的 `rememberUpdatedState` 读取最新连接状态，保留当前导航栈与页面状态。

debug 的 `SettingsStateTestActivity` 复用实际 `SettingsApp`，通过 SINGLE_TOP Intent 模拟异步连接／断开，不重建 Activity。修复前能复现首页持续显示未连接，修复后在 Android 15 模拟器通过首页连接／断开实时刷新、二级页面接收更新后返回首页的回归：

```sh
python3 scripts/check_settings_ui.py --serial emulator-5554 --service-state-only \
  --out app/build/reports/service-state
```

此测试验证 Compose 状态传播，不模拟 Xposed Binder 本身；测试 Activity 不进入 release。

## 二级页面过渡动画

为模块 Activity 的 Navigation 3 显式设置进入、返回及预测返回过渡，复用 Compose 的 `slideInHorizontally`、`slideOutHorizontally`、`fadeIn`、`fadeOut` 和标准缓动。页面以 350 毫秒短距离滑动与 220 毫秒淡入淡出表达层级；普通返回反转方向，预测返回根据手势边缘选择方向，并由导航框架处理完成／取消。入口与用法遵循 [Navigation 3 官方动画接口](https://developer.android.com/guide/navigation/navigation-3/animate-destinations)。

导航容器裁剪越界内容，不执行额外的尺寸动画；保留原有页面保存状态与系统动画缩放行为。未新增动画库，也未改动其他窗口。

Android 15 模拟器已通过模块导航、持久化、搜索和编辑入口回归，以及按钮返回、左右侧手势返回、手势取消检查。录屏及事件时间位于 `app/build/reports/navigation-motion/`，已检查过渡中间帧；debug 构建与 4 个单元测试通过。

## 状态栏与软键盘避让

模块设置页保持 edge-to-edge。导航栈外层消费横向 safeDrawing 边距，
顶部 safeDrawing 由 Material 3 TopAppBar 自身消费：背景覆盖状态栏，标题文字与返回按钮避开状态栏和刘海。
展开、折叠及提起态的背景色均由同一个 TopAppBar 绘制，避免顶部出现页面底色与标题栏的色带分界。
Scaffold 使用底部 safeDrawing（包含 IME）计算内容 PaddingValues，内容根节点应用并消费
这些边距，使列表视口随键盘缩小，底部导航栏与键盘边距不累加。Activity 显式使用
adjustResize，避免系统平移整个窗口时把标题推到状态栏后面。

仅修改模块 Activity 的布局，不改变注入弹窗及其他窗口。复用 Compose WindowInsets，
不监听键盘高度或手工计算状态栏像素。回归命令：

```sh
python3 scripts/check_settings_ui.py --serial emulator-5556 --insets-only
```

已在模拟器验证首页、系统框架二级页的键盘避让、关闭后的高度恢复、
键盘展开时折叠标题及返回导航。测试直接比较列表、标题文字与系统 Insets 的边界，同时确认标题栏背景从窗口顶部开始绘制，
截图保存在 app/build/reports/ui-smoke/insets-*.png。
x86_64 模拟器使用移除 arm64 native 库后重新签名的临时 debug 测试包；交付包保持完整 release。

## 搜索栏吸顶

模块首页与二级设置页使用 Compose `LazyColumn.stickyHeader`：搜索栏随列表上移，
到折叠标题栏下方后固定。搜索栏使用页面底色遮挡从下方滚过的内容，保留上下留白、
原有搜索状态和键盘避让。注入弹窗不受影响。

`check_settings_ui.py --sticky-search-only` 使用 debug 测试 Activity 的长列表验证两个页面从初始位置滚动吸顶、
继续滚动时位置不变，以及吸顶后的搜索与清除操作；`--insets-only` 回归键盘布局。

## 桌面图标显示开关

首页状态卡片下方的“显示桌面图标”直接读取、修改桌面入口 Activity 的组件启用状态，
不写入 Hook 配置或额外偏好。默认按 manifest 启用，切换后立即读回实际状态。
Activity 可见时动态监听当前包的 PACKAGE_CHANGED 广播，收到后重新读取桌面组件状态；
使用非导出的接收器，按 package 数据 URI 精确过滤本应用。onStart 注册后补读，onStop 注销，
既覆盖前台外部变更，也补齐不可见期间的变更。

桌面 MAIN/LAUNCHER 入口直接由无界面、使用独立任务的 LauncherActivity 提供，再打开 SettingsActivity；
SettingsActivity 保留框架 MODULE_SETTINGS 入口。不再使用 activity-alias，也不保留旧组件名或迁移逻辑。
使用 DONT_KILL_APP 切换桌面组件，隐藏图标时保留当前设置页，仍可通过框架打开模块设置。

`check_settings_ui.py --launcher-icon-only` 验证默认入口唯一、图标显示／隐藏、切换时进程保留、
重启及外部组件状态同步，并确认框架设置入口始终可用。debug 的 LauncherStateTestReceiver
通过 PM 修改桌面组件并触发真实系统广播，验证前台开关实时刷新且 Activity 实例、进程保持不变；
该测试接收器不进入 release APK。
Android 16 模拟器需先执行 `adb -s <serial> root`，以允许脚本通过 shell 模拟外部组件状态变更；
模块自身切换桌面组件无需 root 或额外权限。
