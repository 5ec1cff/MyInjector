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
