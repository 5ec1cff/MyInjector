# Rust 桌面动态解析可行性

结论：7751、8584、8593 三份 APK 可以采用同一套规则定位现有 hook 的主要依赖，
不需要为每个版本填写 RVA。已实现并运行离线原型
[`probe_miuihome_resolver.py`](../scripts/probe_miuihome_resolver.py)，完整输出见
[`miuihome-resolver-samples.json`](miuihome-resolver-samples.json)。摘要用于标识样本，
不参与定位或白名单判断。

这次完成的是静态分析和离线原型。模块仍使用 7751 的固定 profile，尚未接入动态
解析器，也没有对这两份其他设备的桌面完成真机注入验证。

## 可用的信息源

三份 APK 的 `libapp.so` 和 `libapp_launcher.so` 都保留了 `.gnu_debugdata`：
它是 XZ 压缩的精简 ELF，里面的 `.symtab` 包含约 7.9 万个 Dart 符号和
2.3 万个 Rust 符号。先前只查看外层 `.dynsym`，遗漏了这个信息源；业务函数虽不能
用 `dlsym` 取得，仍可以从 APK 文件解析出名称、地址和长度。

`.gnu_debugdata` 不在普通 `PT_LOAD` 映射中，正式实现需要读取对应 APK/ELF
文件并解压。原型校验 ELF 架构、文件边界和可执行段范围，并限制解压输出大小。

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

7751 的八处 hook 和六个 Rust 地址全部复现现有 `profile_7751.h`。新样本的具名
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
指针 delta 字段在三份中一致，原型仍从具名 getter 等代码提取。三个样本的 VM
snapshot 版本哈希均为 `5d85d85296400e9b3992193deed317c9`；这说明 snapshot
格式版本一致，不能据此推断业务 CID 或全部运行时 ABI 相同。

单纯屏蔽地址立即数后扫描短指纹已经出现反例：旧版纵向拖动结束的 32 字节前缀，
在新版匹配到了另一个函数，真正目标因异步上下文分配变化而未匹配。因此不宜把
“唯一字节匹配”当作正确性的充分条件。

## 接入模块的方式与边界

正式实现可在 native 初始化线程中读取文件，以有大小限制的 XZ 解码器解出符号表，
按上述关系构造不可变的 RuntimeProfile，再让 hook 和广播代码读取该 profile。
对象字段、CID 和闭包地址也一起放入 profile，替换 `native_hook.cpp` 中对应常量。
文件读取、解压和扫描均在安装 hook 前完成，不放入 Dart 观察回调。

APK/ELF 摘要可以保留为解析缓存键，应用更新后重新解析；无需继续要求摘要属于
某个桌面版本白名单。仍要校验支持的 Dart VM ABI、入口寄存器约定、对象读取路径、
Rust 返回值及 Arc/Mutex 布局，并在设备上确认广播接收器和手势链。当前离线原型
尚未完整验证这些运行时条件，不能直接拿输出解除模块限制。

后续若移除 `.gnu_debugdata`、重命名方法或改变编译结构，需要新的解析规则；
现有证据只覆盖这三份样本。缺少信息、候选不唯一或布局校验失败时，应记录具体
原因并停用对应 native 功能，不能继续使用旧 RVA。

## 重现

需要 Python 3.8 或更新版本、`lzma` 标准库和 `capstone` Python 包。

```sh
python3 scripts/probe_miuihome_resolver.py \
  /path/to/MiuiHome-rs.apk \
  /path/to/系统桌面_RELEASE-8.01.02.8584-260918-10231931-R.apk \
  /path/to/系统桌面_RELEASE-8.01.02.8593-260918-10241550-R.apk \
  --output /tmp/miuihome-resolver-samples.json
```

此命令只读取 APK 并输出分析报告，不连接设备或生成供模块直接加载的配置。
