# Rust/Flutter 小米桌面支持

通过 LSPosed 的 HYOS 支持加载 arm64 `libxposed_native_entry`（native API 103）。
安装完整模块 APK，同时启用**系统框架**和 **com.miui.home** 作用域，然后重启设备。
升级后打开一次模块设置，连接服务后会将旧桌面设置迁移到 remote prefs；之后桌面冷启动
不依赖再次打开模块。需要支持当前 native API 103 订阅流控的 LSPosed release 框架。

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

## 远程配置

模块通过现有 libxposed service 将三个开关写入 `miuihome` remote preferences。
HYOS 与 system_server 读取同一组数据，不再用配置广播、Runtime receiver、启动握手，
也不再保存 `/data/system/myinjector_miuihome.pb`。
模块端旧数据导入、离线补交与同步状态见 [配置迁移](remote-preferences.md)。

native API 的 on_loaded 只保存 API 并启动 worker，不在 dlopen 注册期间执行远程 I/O。
worker 先订阅再取快照；每次回调同步读完整快照并原子发布 mask、使旧手势 epoch 失效后返回。
count=0 是全量刷新，clear 使用默认全关。读取失败保留最近成功值，唤醒 worker 定时重试，
稳定时不轮询；订阅底层的冻结、背压与重连由框架处理，模块不重订阅或管理 ACK。
只有首次订阅接入失败时重试建立逻辑订阅。卸载生命周期尚未定义，升级 native 模块需重启宿主。

公开头文件为 103；module metadata 保留 minApiVersion=102、targetApiVersion=103，
以适配当前框架的模块元数据校验与新入口选择。Java API/service 仍使用 102。
Hook 后端改用 context 和 XposedStatus 接口，保留 inline/PLT hook 与 madvise guard。

强停等上行命令仍通过专用 marker Intent 进入 AMS.preStartProcess。
system_server 按调用 UID、Android 用户、任务身份及同组配置校验，再交给自己的线程执行。
marker 使用不存在的包名，避免桥接失效时变成对普通应用的预启动请求。
存储迁移保留预启动、AOSP 信息页与 Toast 的现有实现，功能迁移仍可分别进行。

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
