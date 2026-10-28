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
