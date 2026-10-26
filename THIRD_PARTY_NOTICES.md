# 第三方代码说明

`app/src/main/cpp/miuihome/` 下的 HYOS native 后端文件：

- `lsposed_hook_backend.cpp`
- `lsposed_hook_backend.h`
- `native_api.h`

改编自 [wxxsfxyzm/MiuiBackGestureHook](https://github.com/wxxsfxyzm/MiuiBackGestureHook)，
来源提交为 `b6e582fc32f83bda3d153b12e3ded2d411acf7f3`，采用 Apache License 2.0。
修改后的后端直接解析并串联已加载 runtime 的 GOT 槽，保留原有页保护和 inline hook
接口约定，不附带 LSPlt。许可证原文保存在
`app/src/main/cpp/miuihome/LICENSE.MiuiBackGestureHook`。

`app/src/main/cpp/miuihome/xz/` 中的解码器代码来自
[Tukaani XZ Embedded](https://github.com/tukaani-project/xz-embedded)，
固定来源提交为 `ae63ae3a36ed01724674e8f3d750dc47bf125410`，采用 0BSD 许可证。
按上游的 userspace 嵌入方式复制所需文件，以构建选项启用 SINGLE、CRC64，
符号保持隐藏；调用方将输出限制为 16MiB。许可证原文保存在该目录的 `COPYING`。
