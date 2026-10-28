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
