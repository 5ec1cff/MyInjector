# 允许在设置页面上显示叠加层

在 LSPosed 中启用 `com.android.settings` 作用域，安装新模块后重启设置进程，
该功能会自动启用，适用于设置内的所有 Activity 及弹窗，包括设置的其他进程。

## 实现

入口为 `SettingsHandler` 中独立的 `SettingsAllowOverlays` 子 hook。

使用 dexsql MCP 分析 `~/Settings.apk` 后确认，该 APK 既使用
`Window.setHideOverlayWindows(true)`，也通过 `addSystemFlags()`、`addPrivateFlags()`
及直接修改 `WindowManager.LayoutParams.privateFlags` 隐藏非系统叠加层。
例如，`HideNonSystemOverlayMixin.onStart()` 会添加 `0x80000` 标志，
无障碍服务警告弹窗还会直接修改 `privateFlags`。

本机 Android 37 SDK 源码中，以上入口均使用
`SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS`（`1 << 19`）。模块在设置进程内：

- 在 `ViewRootImpl.setView()` 提交新窗口、`setLayoutParams()` 更新窗口参数之前，
  清除该标志。这也覆盖直接修改参数和页面在 `onStart`/`onResume` 后再次设置标志的情况。
- 在 Android 12 及以上，将 `Window.setHideOverlayWindows()` 的参数改为 `false`。
  两组 hook 分别安装，失败时记录 MyInjector 日志。

无需逐一枚举 Activity；现有模块入口只在 `com.android.settings` 中加载此 handler，
所以框架方法的 hook 也只作用于设置进程。

## 验证

构建命令：`./gradlew :app:assembleDebug`。
本次 Debug 构建通过，`git diff --check` 通过。

设备验证时可保持屏幕内容提取悬浮窗开启，依次进入普通设置页、通知使用权确认页、
设备管理页及无障碍服务警告弹窗，再测试退后台返回、页面跳转和重新打开弹窗。
当前环境没有连接 ADB 设备，尚未验证真机上的叠加层显示。
