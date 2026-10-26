# madvise guard 的依据与适用范围

最初采用这个 guard 的依据是参考项目
[MiuiBackGestureHook 的 README](https://github.com/wxxsfxyzm/MiuiBackGestureHook/blob/b6e582fc32f83bda3d153b12e3ded2d411acf7f3/README.md)
及其 `lsposed_hook_backend.cpp`。参考项目的结论与本机验证需要分开表述。

分析设备为 **Xiaomi 17 Pro Max**，型号 `2509FPN0BC`，设备代号 `popsicle`。
当前系统为 Android 17（SDK 37），系统版本为
`OS4.0.4.0.XPBCNXM`。系统指纹为：

```text
Xiaomi/popsicle/popsicle:17/CP2A.260605.016/OS4.0.4.0.XPBCNXM:user/release-keys
```

本次进一步使用 IDA 检查了从上述设备读取的
`/system_ext/lib64/libhyper_os_flutter.so`，SHA-256 为：

```text
96611d690393c5e274f90acfc1357ad9dbfd089f641986eaa518e1d4f688ff59
```

## 静态分析确认的路径

以下地址均为该 ELF 的 RVA，函数名来自二进制中的符号。

| RVA | 函数 | 确认的行为 |
| --- | --- | --- |
| `0x1145098` | `flutter::ReleaseApkAndSoColdMmapPages(int)` | 读取 `/proc/self/smaps` 和 `/proc/self/maps`，筛选文件映射，调用驻留页释放函数 |
| `0x11458b4` | `flutter::madvise_resident_pages` | 使用 `mincore` 检查驻留情况，合并连续驻留页后调用下一层 |
| `0x1144ff0` | `flutter::(anonymous namespace)::madvise_skipping_pin` | 跳过内置 pin 范围，对其余部分调用 `madvise(..., 4)` |
| `0xe25100` | `Engine::HandleLifecyclePlatformMessage` 的任务闭包 | 检查取消状态及当前生命周期；非 resumed 分支可向 release 线程投递释放任务 |
| `0x6ee354` | `flutter::hyper_dumpMemInfo` 的任务闭包 | 有调用 `ReleaseApkAndSoColdMmapPages(1)` 的路径 |

`madvise_skipping_pin` 的两个调用点可以直接在汇编中确认 advice 参数：

```asm
0x1145034  MOV W2, #4
0x1145038  BL  .madvise
0x1145064  MOV W2, #4
0x1145068  BL  .madvise
```

Android/Linux 中 advice `4` 是 `MADV_DONTNEED`。

## 为什么可能影响 hook

`ReleaseApkAndSoColdMmapPages` 筛选 `r-xp` 映射。路径匹配包括 `.apk`、
以 `lib` 开头的 `.so` 和包含 `hyper_spawner` 的路径；具体选择还受 mode 和 pin 范围
影响。它会再次核对 maps，实际只对驻留页面调用释放函数。

从这些操作及生命周期、内存整理入口看，可以推断其用途是释放文件映射的驻留页、
降低内存占用。函数名虽带 Cold，`mincore` 本身只检查页面是否驻留，不能据此认定
每个候选页都经过访问热度判断。

采样时的桌面 maps 中存在 MiuiHome.apk 的 `r-xp` 映射，且 APK 声明
`extractNativeLibs=false`。因此 APK 内 native 代码页属于可能涉及的映射类别；
修改这些代码页的 inline hook 有受到影响的风险。guard 预先登记补丁页，拦截 runtime
导入的 `madvise`，遇到 `MADV_DONTNEED` 时拆分请求、跳过登记页，其余范围正常转发。

## 证据边界

- 已确认本机样本存在上述回收路径和 `MADV_DONTNEED` 调用。
- 尚未抓取本模块运行时的调用参数、调用栈与补丁页范围，不能断言八处 hook 页实际被命中。
- 上述路径筛选的是 `r-xp`，不能据此笼统断言 GOT 页也会被清理。后端登记 GOT 页是保守保护策略。
- guard 仅覆盖经过该 runtime 导入槽的 `madvise` 调用。
