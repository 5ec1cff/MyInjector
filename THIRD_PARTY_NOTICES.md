# Third-party notices

The HYOS native backend files under `app/src/main/cpp/miuihome/`:

- `lsposed_hook_backend.cpp`
- `lsposed_hook_backend.h`
- `native_api.h`

are adapted from [wxxsfxyzm/MiuiBackGestureHook](https://github.com/wxxsfxyzm/MiuiBackGestureHook),
commit `b6e582fc32f83bda3d153b12e3ded2d411acf7f3`, under Apache License 2.0.
The modified backend resolves and chains the loaded runtime's GOT slots directly
and does not bundle LSPlt. The original page protection and inline-hook contracts
are retained. A copy of the license is in
`app/src/main/cpp/miuihome/LICENSE.MiuiBackGestureHook`.
