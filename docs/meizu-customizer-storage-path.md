# 魅族主题美化（com.meizu.customizecenter）存储路径适配

## Hook 原理

`MeiZuCustomizerCenterHandler` 挂钩 `Environment.getExternalStorageDirectory()`，
当调用栈中紧随其后的帧来自主题美化里在 `<clinit>` 中构造存储路径的常量类时，
把返回目录重定向到其 `Documents` 子目录。两处目标类都初始化完成后自动卸载。

## 版本对照

### 主题美化 11.6.1（旧适配代码 MyInjector-281 的目标）

- `sa.a`：`<clinit>` 内直调 `getExternalStorageDirectory()`，构造
  `<ext>/Customize`、`TestLicense`、`PatchLicense`、
  `<ext>/Android/data/com.meizu.customizecenter/TempImage/` 等路径。
- `com.meizu.net.lockscreenlibrary.admin.constants.Constants`：`<clinit>` 内直调三处，
  构造 `CUSTOMIZECENTER_HOME_PATH`（`<ext>/Customize`）、
  `NATIVELOCKSCREEN_HOME_PATH`（`<ext>/Android/obb`）、
  `FILE_PATH_WITH_SD`（`<ext>/Android/data/com.meizu.customizecenter/`）。

### 主题美化 12.5.0（本次适配）

- `sa.a` 整体重命名为 `fb.b`：`<clinit>` 逻辑与 11.6.1 逐行一致
  （同样的 `Customize`/`TestLicense`/`PatchLicense`/`TempImage` 路径构造，
  仅新增 `FLYME_VERSION` 常量）。
- `com.meizu.net.lockscreenlibrary.admin.constants.Constants` 保持原名同包，
  `<clinit>` 结构不变，仍直调三处 `getExternalStorageDirectory()`。
- 其余混淆名整体发生字母表平移（如 `me.a`→`ue.a`、`he.a`→`pe.a`、`le.f`→`te.f`），
  但它们不在本 hook 的识别范围内，无需处理。

## 本次改动

- `customizePathClasses = setOf("sa.a", "fb.b")`：新旧版本的常量类都识别，
  任一命中即视为主题路径初始化完成；两个类都存在时只有实际加载的那个会出现在栈上。
- 自动卸载条件相应改为「任一常量类命中 且 Constants 命中」，
  在 11.x 与 12.x 上都会在两类初始化完成后正常卸载。
- 重定向与日志行为与旧版保持一致（每个类仅首次调用重定向）。

## 验证边界

以上结论来自对工作区两个 APK 的 JADX 反编译源码静态比对
（jadx 对两 APK 分别报 64/若干错误，均为复杂第三方库常见现象，目标类反编译完整可读）。
未连接魅族设备，未验证重定向后的实际下载/应用行为；类加载顺序异常时 hook 可能不生效。
