# Rust/Flutter 小米桌面支持

通过 LSPosed 的 HYOS 支持加载 arm64 `native_init` 入口，使用旧版 Hook/Unhook API。
安装完整模块 APK，同时启用**系统框架**和 **com.miui.home** 作用域，然后重启设备。
升级后打开一次模块设置，连接服务后会将旧设置迁移到 remote prefs。
桌面冷启动通过系统桥接读取已保存配置，不依赖再次打开模块，也不依赖尚未公开的 native API 103。

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

## 远程配置与广播桥接

模块通过 libxposed service 102 将三个开关写入 `miuihome` remote preferences。
system_server 通过 Java API 从同一组读取配置并监听变化，校验特权命令时也使用该配置。
模块端旧数据导入、离线补交和自动同步保持不变，见 [配置迁移](remote-preferences.md)。
不再由模块 Activity 广播 protobuf，也不恢复 system_server/宿主的配置文件持久化。
module metadata 使用 minApiVersion=102、targetApiVersion=102。

HYOS 使用恢复后的旧通道消费配置：

1. native 等待桌面的 Runtime 初始化，通过 Broadcast_register_receiver 注册
   `io.github.a13e300.myinjector.miuihome.CONFIG` 接收器。发送者必须持有
   `android.permission.BROADCAST_PACKAGE_REMOVED` 系统权限，普通应用不能伪造下发。
2. 注册后通过 marker Intent 请求初始快照；首次收到配置之前每秒重试，最多 60 次。
   稳定状态由广播驱动，空闲 worker 阻塞于 eventfd。
3. system_server 在桥接就绪、Java 配置更新、初始握手及正常卸载时广播快照；
   卸载时下发全关配置。已有配置直接从框架读取，不依赖打开模块 Activity。

广播显式发给 `com.miui.home`，携带 protocol、mask 和单调递增的 revision。
mask 的位 0、1、2 分别对应拖动强停、AOSP 设置、禁用预启动。
native 拒绝未知协议、非法位和过期版本；worker 切换配置并使旧手势失效。

接收器沿用动态解析器校验的 Rust Arc/Mutex 布局和 native API 所有权规则。
原 inline/PLT hook 与 madvise guard 不变；不再包含 native 103 头文件或远程订阅代码。

配置握手和强停等上行命令仍通过专用 marker Intent 进入 AMS.preStartProcess。
system_server 按调用 UID、Android 用户、任务身份及同组配置校验，再交给自己的线程执行。
marker 使用不存在的包名，避免桥接失效时变成对普通应用的预启动请求。
预启动、AOSP 信息页及 Toast 保持原有实现。

## 构建与验证

```sh
./gradlew :app:assembleRelease
python3 scripts/check_miuihome_native.py \
  --launcher /path/to/MiuiHome-rs.apk \
  --launcher /path/to/系统桌面_RELEASE-8.01.02.8584-260918-10231931-R.apk \
  --launcher /path/to/系统桌面_RELEASE-8.01.02.8593-260918-10241550-R.apk \
  --module app/build/outputs/apk/release/MyInjector-<版本>-release.apk
```

检查需要主机的 Clang、CMake 和 Python 标准库，自动编译模块的同一份 C++ 解析器。
离线检查覆盖配置乱序、手势计时与身份、三份样本的动态解析和模拟内存映像校验，
以及损坏调试数据、未知 VM ABI、调用约定变化、符号歧义等拒绝路径。
同时检查模块 native 入口、导出符号及 APK/ELF 的 16KB 对齐。
目前尚未完成这份实现的真机注入和交互验收；离线通过不能证明设备上的回调链已经跑通。

native 库更新需要重新加载 HYOS 进程，仅 Java 热重载不能替换已映射的 `.so`。
通过 PackageManager 安装完整 APK，保留上一份完整模块 APK 便于回退。
日志标签为 `MyInjectorMiuiHome` 和 `MyInjectorHyosMadvise`。
