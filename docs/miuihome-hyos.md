# Rust/Flutter 小米桌面支持

通过 LSPosed 的 HYOS 支持加载 arm64 `native_init` 入口。安装完整模块 APK，
同时启用**系统框架**和 **com.miui.home** 作用域，然后重启设备。升级后打开一次
模块设置，把已有桌面开关同步给 system_server；之后仍使用原来的三个开关。

模块在安装 hook 前解析 `libapp.so`、`libapp_launcher.so` 的 `.gnu_debugdata`、
指令结构和重定位，动态取得八处 hook、对象字段、事件 CID、Runtime 和广播虚表。
已用同一份 C++ 解析器验证 7751、8584、8593 三份 APK；不按桌面版本号或完整文件
SHA 白名单放行。支持的 Dart VM ABI、入口参数和 Arc/Mutex 布局仍须通过校验，
信息缺失、候选有歧义或布局变化时停用 native hook，并在日志中说明原因。
旧 Java 桌面继续使用 `MiuiHomeHandler`。具体规则和边界见
[动态解析实现](miuihome-dynamic-resolver.md)。

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
不分配对象、不执行 Binder 调用。后端对本模块修改的页设置 `madvise` guard，
防范 runtime 冷页回收路径丢弃补丁，支持 16KB 页。已静态确认本机 runtime 存在
该路径，尚未跟踪它是否实际命中这些补丁页；来源、地址及证据边界见
[madvise 分析](miuihome-madvise.md)。

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

接收器使用经实际构造指令校验的 `RArc<RMutex<BroadcastReceiver_TO<RBox<()>>>>` 布局，复用
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
  --launcher /path/to/系统桌面_RELEASE-8.01.02.8584-260918-10231931-R.apk \
  --launcher /path/to/系统桌面_RELEASE-8.01.02.8593-260918-10241550-R.apk \
  --module app/build/outputs/apk/debug/MyInjector-<版本>-debug.apk
```

检查需要主机的 Clang、CMake 和 Python 标准库，自动编译模块的同一份 C++ 解析器。
离线检查覆盖配置乱序、手势计时与身份、三份样本的动态解析和模拟内存映像校验，
以及损坏调试数据、未知 VM ABI、调用约定变化、符号歧义等拒绝路径。
同时检查模块 native 入口、导出符号及 APK/ELF 的 16KB 对齐。
目前尚未完成这份实现的真机注入和交互验收；离线通过不能证明设备上的回调链已经跑通。

native 库更新需要重新加载 HYOS 进程，仅 Java 热重载不能替换已映射的 `.so`。
通过 PackageManager 安装完整 APK，保留上一份完整模块 APK 便于回退。
日志标签为 `MyInjectorMiuiHome` 和 `MyInjectorHyosMadvise`。
