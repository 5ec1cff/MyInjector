# Rust 桌面动态解析实现

结论：7751、8584、8593 三份 APK 可以采用同一套规则定位现有 hook 的主要依赖，
不需要为每个版本填写 RVA。动态解析已接入模块，C++ 实现在
[`runtime_profile.cpp`](../app/src/main/cpp/miuihome/runtime_profile.cpp) 和
[`resolver_elf.cpp`](../app/src/main/cpp/miuihome/resolver_elf.cpp)。最初的离线原型保留在
[`probe_miuihome_resolver.py`](../scripts/probe_miuihome_resolver.py)，完整输出见
[`miuihome-resolver-samples.json`](miuihome-resolver-samples.json)。摘要用于标识样本，
不参与定位或白名单判断。

模块已移除固定 `profile_7751.h` 和桌面完整文件摘要白名单。三份样本均通过主机上的
同一份 C++ 解析器及模拟加载检查；尚未完成真机注入和交互验证。

## 可用的信息源

三份 APK 的 `libapp.so` 和 `libapp_launcher.so` 都保留了 `.gnu_debugdata`：
它是 XZ 压缩的精简 ELF，里面的 `.symtab` 包含约 7.9 万个 Dart 符号和
2.3 万个 Rust 符号。先前只查看外层 `.dynsym`，遗漏了这个信息源；业务函数虽不能
用 `dlsym` 取得，仍可以从 APK 文件解析出名称、地址和长度。

`.gnu_debugdata` 不在普通 `PT_LOAD` 映射中，模块读取对应 APK/ELF 文件并解压。
读取支持独立 ELF 或 `APK!/lib/arm64-v8a/*.so` 的 STORED 条目，校验 ZIP/ELF
边界、架构、函数范围和候选唯一性。内置 XZ Embedded，解压输出限制为 16MiB，
校验 CRC32/CRC64；未知压缩方式、损坏内容或超出限制均拒绝解析。

定位时结合以下关系：

- Dart：具名方法、方法内的指令结构、闭包到业务方法的调用、对象分配 stub。
  同名符号可能同时包含闭包包装器和方法本体，不能取第一个。IDA 为重名符号添加的
  `_0` 等后缀也不是原始符号名。
- Rust：忽略会变化的 crate 哈希，匹配符号路径中的角色，再通过
  `R_AARCH64_RELATIVE` 重定位寻找虚表，校验表头和各方法的关系。
- 广播接收器存在四个符合初筛条件的 Arc 虚表。继续检查实际
  `Context_register_receiver_impl` 同时引用 Receiver、RBox 和哪个 Arc 虚表，
  三个样本均能消除歧义。
- Runtime：在 `App::on_attach` 内分析全局取址、原子状态检查和
  `content_resolver_new` 的 PLT/导入关系。三个独立调用点指向同一个全局槽，
  并且状态地址均为槽地址加 8。

## 三份样本的结果

以下均为各自 ELF 的 RVA；拖动开始是方法内部已经接受手势的位置，其余为回调入口。

| 定位项 | 7751 | 8584 | 8593 |
| --- | --- | --- | --- |
| 拖动开始 | `0x11ad39c` | `0x11fd574` | `0x11f6aa8` |
| 纵向拖动结束 | `0x11aabf4` | `0x11fae68` | `0x11f439c` |
| 横向拖动结束 | `0x11ac694` | `0x11fc938` | `0x11f5e6c` |
| 清理提交 | `0xdf4804` | `0xe50880` | `0xe34ecc` |
| 信息按钮按下 | `0x1234eb8` | `0x1285394` | `0x127e8c4` |
| 信息按钮松开 | `0x1234de4` | `0x12852c0` | `0x127e7f0` |
| 信息按钮 void 闭包 | `0x1238838` | `0x1288d84` | `0x12822b4` |
| 指针事件处理 | `0x190a258` | `0x197aebc` | `0x197aaa0` |
| Runtime 槽 | `0xf21058` | `0xf26258` | `0xf26dd8` |
| RString 虚表 | `0xeb6f50` | `0xebc4c0` | `0xebcfc8` |
| Receiver 虚表 | `0xe139b0` | `0xe18fe8` | `0xe19a48` |
| RBox 虚表 | `0xe3ca98` | `0xe3dc20` | `0xe3e680` |
| Mutex 虚表 | `0xeb76b8` | `0xebcc28` | `0xebd730` |
| Arc 虚表 | `0xe995f0` | `0xe9eb58` | `0xe9f660` |

7751 的八处 hook 和六个 Rust 地址全部复现原固定 profile。新样本的具名
方法入口也与 IDA 的结果交叉核对。Runtime/RString 的定位另用参考项目
[MiuiBackGestureHook 的解析器](https://github.com/wxxsfxyzm/MiuiBackGestureHook/blob/b6e582fc32f83bda3d153b12e3ded2d411acf7f3/miui-home-hyos-native/runtime_profile_resolver.cpp) 在
主机上构造 ELF 内存映像验证，三份均得到相同结果。

对象布局也要解析，不能只寻找函数：

| 布局项 | 7751 | 8584 / 8593 | 提取依据 |
| --- | --- | --- | --- |
| `controller.task` | `0x21b` | `0x21f` | 应用信息方法中的字段加载 |
| 菜单 State CID | `0x1b05` | `0x1ba3` | `MenuItemButton.createState` 的分配 stub |
| 原始 Down CID | `0x19c6` | `0x1a5d` | `copyWith` 的分配 stub |
| 原始 Move CID | `0x19c4` | `0x1a5b` | 同上 |
| 原始 Up CID | `0x19c2` | `0x1a59` | 同上 |
| 原始 Cancel CID | `0x19ba` | `0x1a51` | 同上 |
| 转换后 Down CID | `0x16e5` | `0x1779` | `transformed` 的分配 stub |
| 转换后 Move CID | `0x16e3` | `0x1777` | 同上 |
| 转换后 Up CID | `0x16e1` | `0x1775` | 同上 |
| 转换后 Cancel CID | `0x16cf` | `0x1763` | 同上 |

任务身份相关的 `model.info`、`info.key`、taskId、userId、Intent/Component 路径和
指针 delta 字段在三份中一致，解析器仍从具名 getter 等代码提取。State 到 widget、
按钮到 onPressed、闭包上下文及 transformed 事件的 delegate 路径也从指令中提取。
三个样本的 VM
snapshot 版本哈希均为 `5d85d85296400e9b3992193deed317c9`；这说明 snapshot
格式版本一致，不能据此推断业务 CID 或全部运行时 ABI 相同。

单纯屏蔽地址立即数后扫描短指纹已经出现反例：旧版纵向拖动结束的 32 字节前缀，
在新版匹配到了另一个函数，真正目标因异步上下文分配变化而未匹配。因此不宜把
“唯一字节匹配”当作正确性的充分条件。

## 接入模块的方式与边界

worker 在安装 hook 前读取文件、解压符号表并构造不可变的 `RuntimeProfile`，
`native_hook.cpp` 和 `native_broadcast.cpp` 共用其中的地址、字段和 CID。
每个桌面进程解析一次，不持久缓存结果；应用更新后，新的进程重新解析。
文件读取、解压和扫描均不放入 Dart 观察回调。

安装前还将文件的 `PT_LOAD` 与实际加载段逐项比较，计算两侧可执行段的 SHA-256，
并核对八处入口字节与五组虚表的实际重定位结果。这里的摘要只用于比较文件和内存，
没有预设某个桌面文件的正确摘要，防止应用更新后拿新文件去修改旧映像。

目前支持上文列出的 VM snapshot 格式和 arm64 compressed pointers；对象头、
字符串、闭包入口及坐标对象的 VM/框架基本布局沿用已分析 ABI。业务字段和 CID
单独解析，不能用 snapshot 版本代替它们。入口参数约定、接收器构造代码、Arc 的
16 字节计数头及后续 40 字节 MutexReceiver 都须通过指令校验；接收器注册前再次
检查虚表。原生 API 的 C 调用声明仍对应已分析 ABI，需要真机确认完整的调用链，
不能把主机解析成功视为其他设备系统库已经兼容。

后续若移除 `.gnu_debugdata`、重命名方法或改变编译结构，需要新的解析规则；
现有证据只覆盖这三份样本。缺少信息、候选不唯一或布局校验失败时，应记录具体
原因并停用对应 native 功能，不能继续使用旧 RVA。

## 重现

用模块自身的 C++ 实现检查三份样本（需要 Clang、CMake 和 Python 标准库）：

```sh
python3 scripts/check_miuihome_native.py \
  --launcher /path/to/MiuiHome-rs.apk \
  --launcher /path/to/系统桌面_RELEASE-8.01.02.8584-260918-10231931-R.apk \
  --launcher /path/to/系统桌面_RELEASE-8.01.02.8593-260918-10241550-R.apk
```

检查会对比已复核样本结果、构造 `PT_LOAD` 和 RELATIVE 重定位，验证正常映像通过、
代码或虚表变化被拒绝，并检查缺少/损坏调试节、未知 VM ABI、入口参数约定变化、
Arc 布局变化、同名函数歧义及 ZIP 错误。ASan/UBSan 构建可通过 `--resolver`
指定同一 CMake 目标 `miuihome_resolver_cli`。

初始 Python 原型用于分析对照，需要 Python 3.8 或更新版本、`lzma` 标准库和
`capstone` Python 包：

```sh
python3 scripts/probe_miuihome_resolver.py \
  /path/to/MiuiHome-rs.apk \
  /path/to/系统桌面_RELEASE-8.01.02.8584-260918-10231931-R.apk \
  /path/to/系统桌面_RELEASE-8.01.02.8593-260918-10241550-R.apk \
  --output /tmp/miuihome-resolver-samples.json
```

此命令只读取 APK 并输出分析报告，不连接设备或生成供模块直接加载的配置。
