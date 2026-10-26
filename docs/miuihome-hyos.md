# Rust/Flutter 小米桌面支持

通过 LSPosed 的 HYOS 支持加载 arm64 `native_init` 入口。安装完整模块 APK，
同时启用**系统框架**和 **com.miui.home** 作用域，然后重启设备。升级后打开一次
模块设置，把已有桌面开关同步给 system_server；之后仍使用原来的三个开关。

当前适配 `RELEASE-8.01.02.7751-260904-10100945-R`。安装 hook 前校验
`libapp_launcher.so`、`libapp.so` 的 SHA-256 和八处 Dart 指令指纹；广播接收器的
Runtime、Arc、Mutex 和虚表布局也限定于这个版本。未知桌面版本不安装 native hook。
旧 Java 桌面继续使用 `MiuiHomeHandler`。

## 功能与实现

- **禁用预启动**：在 system_server 拦截来自桌面 UID 的
  `AMS.preStartProcess(String, Intent, int)`。这个功能独立于 native 版本适配。
- **拖动强停**：native 观察 Dart 的拖动开始、纵横向结束、指针取消和真实清理提交。
  松手时冻结时长，达到 700ms 且提交的 taskId、userId、包名均匹配时，请求
  system_server 执行 `forceStopPackage`。取消、换卡和配置变化会撤销标记；
  不把删除动画计入拖动时间。准备强停时显示“停止进程”Toast，未修改 Flutter 卡片标题。
- **长按应用信息**：按住最近任务菜单的信息按钮 500ms，通过 system_server 的
  `TaskStackBuilder` 打开 AOSP `SubSettings / AppInfoDashboardFragment`。
  短按保留原行为，移动超过阈值或取消指针会撤销长按。只消费该按钮的 void
  `onPressed` 闭包，异步 Dart 方法仍执行原逻辑。

新版桌面没有 `FORCE_STOP_PACKAGES` 权限，因此强停由 system_server 执行。
native 在 Dart 所在线程中复制任务身份，通过队列交给 worker；不跨线程保存 Dart
对象。汇编入口保存寄存器、浮点状态和 Dart 的 X15 栈，观察回调中不进入 VM、
不分配对象、不执行 Binder 调用。后端保护本模块修改的页，避免 runtime 的
`MADV_DONTNEED` 丢弃 hook；支持 16KB 页。

## 配置广播

配置不再借用 `hasSystemFeature`，也不周期查询 PackageManager。

1. 模块界面把原 `MiuiHomeConfig` protobuf 和 PendingIntent 凭证发送给
   system_server。服务端验证凭证的创建包，原子保存配置后广播最新快照。
2. native 等待桌面的 Runtime 初始化，通过 `Broadcast_register_receiver` 注册
   `io.github.a13e300.myinjector.miuihome.CONFIG` 接收器。接收器要求发送者持有
   `android.permission.BROADCAST_PACKAGE_REMOVED` 系统权限，阻止普通应用伪造下发。
3. 注册后请求一次初始快照，解决桌面重启时漏掉此前广播的问题。首次收到配置之前，
   每秒重试握手，最多 60 次；之后完全等待广播，空闲 worker 阻塞于 eventfd。
   system_server 初始化、配置变更及正常卸载 hook 时也会主动广播；卸载下发全关快照。

广播显式发给 `com.miui.home`，携带 `protocol=1`、`mask` 和单调递增的 `revision`。
mask 的位 0、1、2 分别对应拖动强停、AOSP 设置、禁用预启动。native 拒绝未知协议、
非法位和过期版本；回调只发布配置并唤醒 worker，由 worker 切换配置、撤销旧手势。

接收器使用 7751 的 `RArc<RMutex<BroadcastReceiver_TO<RBox<()>>>>` 布局，复用
桌面的 Arc/Mutex 实现，为自身回调和析构复制虚表；持有 Runtime 引用，退出时注销并
释放引用。Intent、RString、返回错误及 pending result 按各自 native API 的所有权释放。

首次握手与强停等上行命令仍通过专用 marker Intent 进入 `AMS.preStartProcess`。
system_server 按调用 UID 和 Android 用户校验桌面身份，再交给自己的线程执行。
marker 使用不存在的包名，避免桥接失效时变成对普通应用的预启动请求。

## 构建与验证

```sh
./gradlew :app:assembleDebug
python3 scripts/check_miuihome_native.py \
  --launcher /path/to/MiuiHome-rs.apk \
  --module app/build/outputs/apk/debug/MyInjector-<版本>-debug.apk
```

离线检查覆盖配置版本与乱序、手势计时与取消、跨用户任务身份、重复提交、SHA-256、
目标 APK 的指纹及广播布局，以及模块 native 入口和 APK/ELF 的 16KB 对齐。
目前尚未完成这份实现的真机注入和交互验收；离线通过不能证明设备上的回调链已经跑通。

native 库更新需要重新加载 HYOS 进程，仅 Java 热重载不能替换已映射的 `.so`。
通过 PackageManager 安装完整 APK，保留上一份完整模块 APK 便于回退。
日志标签为 `MyInjectorMiuiHome` 和 `MyInjectorHyosMadvise`。
